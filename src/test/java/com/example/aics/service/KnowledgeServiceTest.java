package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.aics.config.RagProperties;
import com.example.aics.dto.CreateKnowledgeBaseRequest;
import com.example.aics.entity.KnowledgeBase;
import com.example.aics.entity.KnowledgeDocument;
import com.example.aics.mapper.KnowledgeBaseMapper;
import com.example.aics.mapper.KnowledgeChunkMapper;
import com.example.aics.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createKnowledgeBaseStoresCurrentUserId() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeService service = service(knowledgeBaseMapper, mock(KnowledgeDocumentMapper.class));
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("售后知识库");
        request.setDescription("售后问题");

        service.createKnowledgeBase(7L, request);

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("售后知识库", captor.getValue().getName());
    }

    @Test
    void ensureKnowledgeBaseOwnedRejectsMissingOrForeignKnowledgeBase() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        KnowledgeService service = service(knowledgeBaseMapper, mock(KnowledgeDocumentMapper.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.ensureKnowledgeBaseOwned(7L, 100L));

        assertEquals("知识库不存在或无权访问", exception.getMessage());
    }

    @Test
    void deleteDocumentRemovesLocalUploadedFile() throws Exception {
        Path uploaded = tempDir.resolve("1").resolve("manual.txt");
        Files.createDirectories(uploaded.getParent());
        Files.writeString(uploaded, "content");

        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        knowledgeBase.setUserId(7L);
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(knowledgeBase);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(99L);
        document.setKnowledgeBaseId(1L);
        document.setFilePath(uploaded.toString());
        when(documentMapper.selectById(99L)).thenReturn(document);

        KnowledgeService service = service(knowledgeBaseMapper, documentMapper, chunkMapper, vectorSearchService);

        service.deleteDocument(7L, 99L);

        assertFalse(Files.exists(uploaded));
        verify(chunkMapper).delete(any(Wrapper.class));
        verify(documentMapper).deleteById(99L);
        verify(vectorSearchService).deleteByDocumentId(99L);
    }

    private KnowledgeService service(KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeDocumentMapper documentMapper) {
        return service(
                knowledgeBaseMapper,
                documentMapper,
                mock(KnowledgeChunkMapper.class),
                mock(VectorSearchService.class)
        );
    }

    private KnowledgeService service(KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeDocumentMapper documentMapper,
                                     KnowledgeChunkMapper chunkMapper,
                                     VectorSearchService vectorSearchService) {
        RagProperties properties = new RagProperties();
        properties.setFileStorageDir(tempDir.toString());
        return new KnowledgeService(
                knowledgeBaseMapper,
                documentMapper,
                chunkMapper,
                mock(DocumentParserService.class),
                mock(TextChunkService.class),
                vectorSearchService,
                properties,
                new ObjectMapper()
        );
    }
}
