package com.example.aics.service;

import com.example.aics.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkServiceTest {

    @Test
    void splitReturnsChunksForTxtOrMarkdownContent() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(220);
        properties.setChunkOverlap(40);
        TextChunkService service = new TextChunkService(properties);

        String text = "售后政策说明。\n\n"
                + "用户购买产品后 7 天内可以申请无理由退货，超过 7 天需要人工审核。"
                + "维修服务需要提供订单号和故障描述。";

        List<String> chunks = service.split(text);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).contains("售后政策"));
    }

    @Test
    void splitReturnsEmptyListWhenParsedTextIsBlank() {
        TextChunkService service = new TextChunkService(new RagProperties());

        assertEquals(List.of(), service.split("  \r\n  "));
    }
}
