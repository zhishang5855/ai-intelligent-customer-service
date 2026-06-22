package com.example.aics.service;

import com.example.aics.dto.ChatRequest;
import com.example.aics.dto.ChatResponse;
import com.example.aics.dto.SourceChunk;
import com.example.aics.entity.Conversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagChatService {

    private final ChatClient chatClient;
    private final VectorSearchService vectorSearchService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final String ragPromptTemplate;
    private final String generalPromptTemplate;

    public RagChatService(ChatClient.Builder chatClientBuilder,
                          VectorSearchService vectorSearchService,
                          ConversationService conversationService,
                          ObjectMapper objectMapper) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.vectorSearchService = vectorSearchService;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.ragPromptTemplate = readPrompt("prompts/rag-answer.st");
        this.generalPromptTemplate = readPrompt("prompts/general-chat.st");
    }

    public ChatResponse chat(ChatRequest request) {
        Conversation conversation = conversationService.getOrCreate(
                request.getConversationId(),
                request.getUserId(),
                request.getQuestion()
        );
        conversationService.saveMessage(conversation.getId(), "user", request.getQuestion(), null);

        String history = conversationService.recentHistory(conversation.getId(), 8);
        boolean useKnowledgeBase = request.getKnowledgeBaseId() != null;
        List<SourceChunk> sources = useKnowledgeBase
                ? vectorSearchService.search(request.getKnowledgeBaseId(), request.getQuestion())
                : List.of();
        String prompt = useKnowledgeBase
                ? buildRagPrompt(request.getQuestion(), history, sources)
                : buildGeneralPrompt(request.getQuestion(), history);

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        conversationService.saveMessage(conversation.getId(), "assistant", answer, toJson(sources));

        ChatResponse response = new ChatResponse();
        response.setConversationId(conversation.getId());
        response.setAnswer(answer);
        response.setSources(sources);
        return response;
    }

    private String buildGeneralPrompt(String question, String history) {
        return generalPromptTemplate
                .replace("{history}", history == null ? "" : history)
                .replace("{question}", question);
    }

    private String buildRagPrompt(String question, String history, List<SourceChunk> sources) {
        String context = sources.isEmpty()
                ? "未检索到相关知识库内容。"
                : sources.stream()
                .map(source -> "来源：" + source.getDocumentName()
                        + "\n片段ID：" + source.getChunkId()
                        + "\n内容：" + source.getContent())
                .collect(Collectors.joining("\n\n"));
        return ragPromptTemplate
                .replace("{history}", history == null ? "" : history)
                .replace("{context}", context)
                .replace("{question}", question);
    }

    private String readPrompt(String location) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource(location).getInputStream(),
                StandardCharsets.UTF_8
        );
    }

    private String toJson(List<SourceChunk> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
