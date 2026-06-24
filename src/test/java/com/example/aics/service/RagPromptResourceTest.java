package com.example.aics.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPromptResourceTest {

    @Test
    void generalChatPromptIsPackaged() {
        assertTrue(new ClassPathResource("prompts/general-chat.st").exists());
    }

    @Test
    void ragAnswerPromptIsPackaged() {
        assertTrue(new ClassPathResource("prompts/rag-answer.st").exists());
    }
}
