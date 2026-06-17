package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aics.config.RagProperties;
import com.example.aics.dto.CreateKnowledgeBaseRequest;
import com.example.aics.entity.KnowledgeBase;
import com.example.aics.entity.KnowledgeChunk;
import com.example.aics.entity.KnowledgeDocument;
import com.example.aics.mapper.KnowledgeBaseMapper;
import com.example.aics.mapper.KnowledgeChunkMapper;
import com.example.aics.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeService {

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

    public KnowledgeBase createKnowledgeBase(CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase;
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                .orderByDesc(KnowledgeBase::getCreatedAt));
    }

    public List<KnowledgeDocument> listDocuments(Long knowledgeBaseId) {
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    @Transactional
    public KnowledgeDocument ingest(Long knowledgeBaseId, MultipartFile file) throws IOException {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + knowledgeBaseId);
        }

        Path storageDir = Path.of(ragProperties.getFileStorageDir(), String.valueOf(knowledgeBaseId));
        Files.createDirectories(storageDir);
        String originalFilename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        Path target = storageDir.resolve(System.currentTimeMillis() + "-" + originalFilename).normalize();
        file.transferTo(target);

        String hash = sha256(target);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(originalFilename);
        document.setFileType(resolveFileType(originalFilename));
        document.setFilePath(target.toString());
        document.setContentHash(hash);
        document.setParseStatus("PROCESSING");
        documentMapper.insert(document);

        try {
            String text = documentParserService.parse(target, originalFilename);
            List<String> chunks = textChunkService.split(text);
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
        } catch (Exception ex) {
            document.setParseStatus("FAILED");
            document.setErrorMessage(ex.getMessage());
            documentMapper.updateById(document);
            throw ex;
        }
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        vectorSearchService.deleteByDocumentId(documentId);
    }

    private String resolveFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return "unknown";
        }
        return fileName.substring(index + 1).toLowerCase();
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
