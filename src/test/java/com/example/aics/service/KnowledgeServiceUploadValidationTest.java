package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.aics.config.RagProperties;
import com.example.aics.entity.KnowledgeBase;
import com.example.aics.mapper.KnowledgeBaseMapper;
import com.example.aics.mapper.KnowledgeChunkMapper;
import com.example.aics.mapper.KnowledgeDocumentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeServiceUploadValidationTest {

    @Test
    void ingestRejectsEmptyFile() {
        KnowledgeService service = serviceWithExistingKnowledgeBase();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.ingest(7L, 1L, file));
    }

    @Test
    void ingestRejectsFileLargerThan20Mb() {
        KnowledgeService service = serviceWithExistingKnowledgeBase();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        assertThrows(IllegalArgumentException.class, () -> service.ingest(7L, 1L, file));
    }

    @Test
    void ingestRejectsUnsupportedExtension() {
        KnowledgeService service = serviceWithExistingKnowledgeBase();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("payload.exe");

        assertThrows(IllegalArgumentException.class, () -> service.ingest(7L, 1L, file));
    }

    private KnowledgeService serviceWithExistingKnowledgeBase() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        knowledgeBase.setUserId(7L);
        when(knowledgeBaseMapper.selectOne(any(Wrapper.class))).thenReturn(knowledgeBase);

        RagProperties ragProperties = new RagProperties();
        ragProperties.setFileStorageDir("target/test-uploads");

        return new KnowledgeService(
                knowledgeBaseMapper,
                mock(KnowledgeDocumentMapper.class),
                mock(KnowledgeChunkMapper.class),
                mock(DocumentParserService.class),
                mock(TextChunkService.class),
                mock(VectorSearchService.class),
                ragProperties,
                new ObjectMapper()
        );
    }
}
