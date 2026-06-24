package com.example.aics.service;

import com.example.aics.dto.ChatRequest;
import com.example.aics.dto.ChatResponse;
import com.example.aics.entity.Conversation;
import com.example.aics.entity.KnowledgeBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceTest {

    @Test
    void chatReturnsDeterministicAnswerWhenKnowledgeBaseHasNoSources() throws Exception {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        VectorSearchService vectorSearchService = mock(VectorSearchService.class);
        ConversationService conversationService = mock(ConversationService.class);
        KnowledgeService knowledgeService = mock(KnowledgeService.class);

        Conversation conversation = new Conversation();
        conversation.setId(10L);
        when(conversationService.getOrCreate(null, 7L, "怎么退货？")).thenReturn(conversation);
        when(conversationService.recentHistory(10L, 8)).thenReturn("");
        when(vectorSearchService.search(1L, "怎么退货？")).thenReturn(List.of());

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        knowledgeBase.setUserId(7L);
        when(knowledgeService.ensureKnowledgeBaseOwned(7L, 1L)).thenReturn(knowledgeBase);

        RagChatService ragChatService = new RagChatService(
                chatClientBuilder,
                vectorSearchService,
                conversationService,
                knowledgeService,
                new ObjectMapper(),
                "dev-placeholder-key",
                ""
        );
        ChatRequest request = new ChatRequest();
        request.setUserId(7L);
        request.setKnowledgeBaseId(1L);
        request.setQuestion("怎么退货？");

        ChatResponse response = ragChatService.chat(request);

        assertEquals(10L, response.getConversationId());
        assertTrue(response.getAnswer().contains("当前知识库中没有找到相关信息"));
        assertTrue(response.getSources().isEmpty());
        verify(chatClient, never()).prompt();
        verify(knowledgeService).ensureKnowledgeBaseOwned(7L, 1L);
    }
}
