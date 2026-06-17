package com.example.aics.controller;

import com.example.aics.dto.ApiResponse;
import com.example.aics.entity.ChatMessage;
import com.example.aics.service.ConversationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long conversationId) {
        return ApiResponse.ok(conversationService.listMessages(conversationId));
    }
}
