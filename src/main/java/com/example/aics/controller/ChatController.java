package com.example.aics.controller;

import com.example.aics.common.AuthInterceptor;
import com.example.aics.common.AuthenticatedUser;
import com.example.aics.dto.ApiResponse;
import com.example.aics.dto.ChatRequest;
import com.example.aics.dto.ChatResponse;
import com.example.aics.service.RagChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping
    public ApiResponse<ChatResponse> chat(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user,
                                          @Valid @RequestBody ChatRequest request) {
        request.setUserId(user.getUserId());
        return ApiResponse.ok(ragChatService.chat(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user,
                             @Valid @RequestBody ChatRequest request) {
        request.setUserId(user.getUserId());
        return ragChatService.stream(request);
    }
}
