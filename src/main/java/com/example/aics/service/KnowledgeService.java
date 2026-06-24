package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aics.common.DocumentIngestException;
import com.example.aics.config.RagProperties;
import com.example.aics.dto.CreateKnowledgeBaseRequest;
import com.example.aics.entity.KnowledgeBase;
import com.example.aics.entity.KnowledgeChunk;
import com.example.aics.entity.KnowledgeDocument;
import com.example.aics.mapper.KnowledgeBaseMapper;
import com.example.aics.mapper.KnowledgeChunkMapper;
import com.example.aics.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final long MAX_UPLOAD_SIZE = 20L * 1024 * 1024;
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("pdf", "docx", "txt", "md", "markdown");

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final DocumentParserService documentParserService;
    private final TextChunkService textChunkService;
    private final VectorSearchService vectorSearchService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public KnowledgeService(KnowledgeBaseMapper knowledgeBaseMapper,
                            KnowledgeDocumentMapper documentMapper,
                            KnowledgeChunkMapper chunkMapper,
                            DocumentParserService documentParserService,
                            TextChunkService textChunkService,
                            VectorSearchService vectorSearchService,
                            RagProperties ragProperties,
                            ObjectMapper objectMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.documentParserService = documentParserService;
        this.textChunkService = textChunkService;
        this.vectorSearchService = vectorSearchService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    public KnowledgeBase createKnowledgeBase(Long userId, CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(userId);
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase;
    }

    public List<KnowledgeBase> listKnowledgeBases(Long userId) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .orderByDesc(KnowledgeBase::getCreatedAt));
    }

    public List<KnowledgeDocument> listDocuments(Long userId, Long knowledgeBaseId) {
        ensureKnowledgeBaseOwned(userId, knowledgeBaseId);
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    @Transactional(noRollbackFor = DocumentIngestException.class)
    public KnowledgeDocument ingest(Long userId, Long knowledgeBaseId, MultipartFile file) throws IOException {
        ensureKnowledgeBaseOwned(userId, knowledgeBaseId);
        validateUpload(file);

        String originalFilename = cleanFilename(file.getOriginalFilename());
        String fileType = resolveFileType(originalFilename);
        if (!SUPPORTED_FILE_TYPES.contains(fileType)) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 PDF、DOCX、TXT、Markdown");
        }

        Path storageDir = Path.of(ragProperties.getFileStorageDir(), String.valueOf(knowledgeBaseId))
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(storageDir);
        Path target = storageDir.resolve(System.currentTimeMillis() + "-" + originalFilename)
                .toAbsolutePath()
                .normalize();
        if (!target.startsWith(storageDir)) {
            throw new IllegalArgumentException("文件名非法");
        }
        file.transferTo(target);

        try {
            validateStoredFile(target, fileType);
        } catch (IOException | RuntimeException ex) {
            deleteUploadedFileQuietly(target);
            throw ex;
        }

        String hash = sha256(target);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(originalFilename);
        document.setFileType(fileType);
        document.setFilePath(target.toString());
        document.setContentHash(hash);
        document.setParseStatus("PROCESSING");
        documentMapper.insert(document);

        try {
            String text = documentParserService.parse(target, originalFilename);
            List<String> chunks = textChunkService.split(text);
            if (chunks.isEmpty()) {
                throw new DocumentIngestException("文档未解析出可用于问答的文本内容，请检查文件内容。");
            }
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setKnowledgeBaseId(knowledgeBaseId);
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunkText);
                chunk.setTokenCount(chunkText.length());
                chunk.setMetadataJson(objectMapper.writeValueAsString(Map.of(
                        "fileName", originalFilename,
                        "chunkIndex", i
                )));
                chunkMapper.insert(chunk);
                vectorSearchService.upsertChunk(chunk.getId(), knowledgeBaseId, document.getId(), originalFilename, chunkText);
            }
            document.setParseStatus("DONE");
            documentMapper.updateById(document);
            return document;
        } catch (DocumentIngestException ex) {
            cleanupFailedIngest(document.getId());
            document.setParseStatus("FAILED");
            document.setErrorMessage(safeErrorMessage(ex));
            documentMapper.updateById(document);
            throw ex;
        } catch (Exception ex) {
            cleanupFailedIngest(document.getId());
            document.setParseStatus("FAILED");
            document.setErrorMessage(safeErrorMessage(ex));
            documentMapper.updateById(document);
            throw new DocumentIngestException("文档解析或向量入库失败，请检查文件内容和模型服务状态", ex);
        }
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        ensureKnowledgeBaseOwned(userId, document.getKnowledgeBaseId());
        deleteStoredFile(document);
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        vectorSearchService.deleteByDocumentId(documentId);
    }

    public KnowledgeBase ensureKnowledgeBaseOwned(Long userId, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, knowledgeBaseId)
                .eq(KnowledgeBase::getUserId, userId));
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不存在或无权访问");
        }
        return knowledgeBase;
    }

    private String resolveFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return "unknown";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("上传文件不能超过 20MB");
        }
    }

    private void validateStoredFile(Path path, String fileType) throws IOException {
        if ("pdf".equals(fileType)) {
            byte[] header = readHeader(path, 5);
            String value = new String(header, java.nio.charset.StandardCharsets.US_ASCII);
            if (!value.startsWith("%PDF-")) {
                throw new IllegalArgumentException("PDF 文件格式不合法");
            }
            return;
        }
        if ("docx".equals(fileType)) {
            validateDocx(path);
            return;
        }
        if ("txt".equals(fileType) || "md".equals(fileType) || "markdown".equals(fileType)) {
            validateUtf8Text(path);
        }
    }

    private byte[] readHeader(Path path, int size) throws IOException {
        byte[] bytes = new byte[size];
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read = inputStream.read(bytes);
            if (read < size) {
                throw new IllegalArgumentException("文件内容不完整");
            }
            return bytes;
        }
    }

    private void validateDocx(Path path) throws IOException {
        boolean hasContentTypes = false;
        boolean hasDocumentXml = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                } else if ("word/document.xml".equals(name)) {
                    hasDocumentXml = true;
                }
                if (hasContentTypes && hasDocumentXml) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("DOCX 文件格式不合法");
    }

    private void validateUtf8Text(Path path) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = Files.newInputStream(path)) {
            bytes = inputStream.readNBytes(8192);
        }
        for (byte value : bytes) {
            if (value == 0) {
                throw new IllegalArgumentException("文本文件包含非法二进制内容");
            }
        }
        try {
            java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("文本文件必须使用 UTF-8 编码");
        }
    }

    private String cleanFilename(String filename) {
        String value = filename == null ? "document" : filename;
        value = value.replace('\\', '/');
        int slashIndex = value.lastIndexOf('/');
        if (slashIndex >= 0) {
            value = value.substring(slashIndex + 1);
        }
        value = value.replaceAll("[\\r\\n\\t]", "_").trim();
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("文件名非法");
        }
        return value;
    }

    private void deleteStoredFile(KnowledgeDocument document) {
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            return;
        }
        Path storageRoot = Path.of(ragProperties.getFileStorageDir()).toAbsolutePath().normalize();
        Path filePath = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(storageRoot)) {
            log.warn("Skip deleting file outside storage root, documentId={}, filePath={}", document.getId(), filePath);
            throw new IllegalStateException("文档记录中的文件路径不在允许删除范围内");
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            log.warn("Delete uploaded file failed, documentId={}, filePath={}", document.getId(), filePath, ex);
            throw new IllegalStateException("文档记录已删除，但本地文件清理失败，请联系管理员处理", ex);
        }
    }

    private void deleteUploadedFileQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Delete invalid uploaded file failed, filePath={}", target, ex);
        }
    }

    private void cleanupFailedIngest(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                    .eq(KnowledgeChunk::getDocumentId, documentId));
            vectorSearchService.deleteByDocumentId(documentId);
        } catch (Exception ignored) {
            // Best-effort cleanup. The document is still marked FAILED for manual retry or cleanup.
        }
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "处理失败";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, messageDigest)) {
                digestInputStream.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
