package com.example.aics.service;

import com.example.aics.common.AiChatException;
import com.example.aics.dto.ChatRequest;
import com.example.aics.dto.ChatResponse;
import com.example.aics.dto.SourceChunk;
import com.example.aics.entity.Conversation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

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
    private final String chatApiKey;
    private final String chatBaseUrl;

    public RagChatService(ChatClient.Builder chatClientBuilder,
                          VectorSearchService vectorSearchService,
                          ConversationService conversationService,
                          ObjectMapper objectMapper,
                          @Value("${spring.ai.openai.api-key:}") String chatApiKey,
                          @Value("${spring.ai.openai.base-url:}") String chatBaseUrl) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.vectorSearchService = vectorSearchService;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.chatApiKey = chatApiKey;
        this.chatBaseUrl = chatBaseUrl;
        this.ragPromptTemplate = readPrompt("prompts/rag-answer.st");
        this.generalPromptTemplate = readPrompt("prompts/general-chat.st");
    }

    public ChatResponse chat(ChatRequest request) {
        validateChatConfig();
        ChatExecution execution = prepareExecution(request);

        String answer = callChatModel(execution.prompt());

        conversationService.saveMessage(execution.conversation().getId(), "assistant", answer, toJson(execution.sources()));

        ChatResponse response = new ChatResponse();
        response.setConversationId(execution.conversation().getId());
        response.setAnswer(answer);
        response.setSources(execution.sources());
        return response;
    }

    public SseEmitter stream(ChatRequest request) {
        validateChatConfig();
        ChatExecution execution = prepareExecution(request);
        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuffer answerBuffer = new StringBuffer();

        try {
            sendEvent(emitter, "meta", objectMapper.writeValueAsString(new StreamMeta(execution.conversation().getId())));
            sendEvent(emitter, "sources", toJson(execution.sources()));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        callChatModelStream(execution.prompt()).subscribe(
                chunk -> {
                    answerBuffer.append(chunk);
                    sendEvent(emitter, "token", chunk);
                },
                error -> {
                    String message = normalizeChatError(error).getMessage();
                    sendEvent(emitter, "error", message);
                    emitter.complete();
                },
                () -> {
                    String answer = answerBuffer.toString();
                    conversationService.saveMessage(
                            execution.conversation().getId(),
                            "assistant",
                            answer,
                            toJson(execution.sources())
                    );
                    sendEvent(emitter, "done", "");
                    emitter.complete();
                }
        );

        return emitter;
    }

    private ChatExecution prepareExecution(ChatRequest request) {
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
        return new ChatExecution(conversation, sources, prompt);
    }

    private void validateChatConfig() {
        if (!StringUtils.hasText(chatApiKey) || "dev-placeholder-key".equals(chatApiKey)) {
            throw new AiChatException("Chat 模型 API Key 未配置，请在 config/local-secrets.yml 中配置 spring.ai.openai.api-key 后重启应用。");
        }
    }

    private String callChatModel(String prompt) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (RestClientException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("server authentication")
                    || message.contains("401")
                    || message.contains("Unauthorized")) {
                throw new AiChatException("Chat 模型认证失败，请检查 config/local-secrets.yml 中的 API Key、base-url 和模型名称。当前 base-url: " + chatBaseUrl, ex);
            }
            throw new AiChatException("Chat 模型调用失败：" + message, ex);
        } catch (RuntimeException ex) {
            throw new AiChatException("Chat 模型调用失败：" + ex.getMessage(), ex);
        }
    }

    private Flux<String> callChatModelStream(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .onErrorMap(this::normalizeChatError);
    }

    private AiChatException normalizeChatError(Throwable error) {
        if (error instanceof AiChatException aiChatException) {
            return aiChatException;
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("server authentication")
                || message.contains("401")
                || message.contains("Unauthorized")) {
            return new AiChatException("Chat 模型认证失败，请检查 config/local-secrets.yml 中的 API Key、base-url 和模型名称。当前 base-url: " + chatBaseUrl, error);
        }
        return new AiChatException("Chat 模型调用失败：" + message, error);
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data == null ? "" : data));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
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

    private record ChatExecution(Conversation conversation, List<SourceChunk> sources, String prompt) {
    }

    private record StreamMeta(Long conversationId) {
    }
}
