package com.example.aics.service;

import com.example.aics.config.RagProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkService {

    private final RagProperties ragProperties;

    public TextChunkService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<String> split(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        if (normalized.isBlank()) {
            return chunks;
        }

        int size = Math.max(200, ragProperties.getChunkSize());
        int overlap = Math.max(0, Math.min(ragProperties.getChunkOverlap(), size / 2));
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + size, normalized.length());
            int adjustedEnd = adjustEnd(normalized, start, end);
            String chunk = normalized.substring(start, adjustedEnd).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (adjustedEnd >= normalized.length()) {
                break;
            }
            start = Math.max(adjustedEnd - overlap, start + 1);
        }
        return chunks;
    }

    private int adjustEnd(String text, int start, int end) {
        if (end == text.length()) {
            return end;
        }
        int paragraphBreak = text.lastIndexOf("\n\n", end);
        if (paragraphBreak > start + 100) {
            return paragraphBreak;
        }
        int lineBreak = text.lastIndexOf('\n', end);
        if (lineBreak > start + 100) {
            return lineBreak;
        }
        int sentenceBreak = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('.', end));
        if (sentenceBreak > start + 100) {
            return sentenceBreak + 1;
        }
        return end;
    }
}
