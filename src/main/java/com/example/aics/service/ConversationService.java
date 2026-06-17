package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aics.entity.ChatMessage;
import com.example.aics.entity.Conversation;
import com.example.aics.mapper.ChatMessageMapper;
import com.example.aics.mapper.ConversationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ConversationService(ConversationMapper conversationMapper, ChatMessageMapper chatMessageMapper) {
        this.conversationMapper = conversationMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Transactional
    public Conversation getOrCreate(Long conversationId, Long userId, String firstQuestion) {
        if (conversationId != null) {
            Conversation existing = conversationMapper.selectById(conversationId);
            if (existing != null) {
                return existing;
            }
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setChannel("WEB");
        conversation.setTitle(buildTitle(firstQuestion));
        conversationMapper.insert(conversation);
        return conversation;
    }

    public ChatMessage saveMessage(Long conversationId, String role, String content, String ragSourcesJson) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setRagSourcesJson(ragSourcesJson);
        chatMessageMapper.insert(message);
        return message;
    }

    public List<ChatMessage> listMessages(Long conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt));
    }

    public String recentHistory(Long conversationId, int limit) {
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("limit " + Math.max(1, limit)));
        StringBuilder builder = new StringBuilder();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            builder.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        return builder.toString();
    }

    private String buildTitle(String question) {
        if (question == null || question.isBlank()) {
            return "新会话";
        }
        String normalized = question.trim();
        return normalized.length() > 30 ? normalized.substring(0, 30) : normalized;
    }
}
