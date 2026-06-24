package com.example.aics.controller;

import com.example.aics.common.AuthInterceptor;
import com.example.aics.common.AuthenticatedUser;
import com.example.aics.dto.ApiResponse;
import com.example.aics.dto.CreateKnowledgeBaseRequest;
import com.example.aics.entity.KnowledgeBase;
import com.example.aics.entity.KnowledgeDocument;
import com.example.aics.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user,
                                             @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.ok(knowledgeService.createKnowledgeBase(user.getUserId(), request));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBase>> list(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user) {
        return ApiResponse.ok(knowledgeService.listKnowledgeBases(user.getUserId()));
    }

    @PostMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<KnowledgeDocument> upload(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user,
                                                 @PathVariable Long knowledgeBaseId,
                                                 @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(knowledgeService.ingest(user.getUserId(), knowledgeBaseId, file));
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user,
                                                              @PathVariable Long knowledgeBaseId) {
        return ApiResponse.ok(knowledgeService.listDocuments(user.getUserId(), knowledgeBaseId));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE) AuthenticatedUser user,
                                            @PathVariable Long documentId) {
        knowledgeService.deleteDocument(user.getUserId(), documentId);
        return ApiResponse.ok(null);
    }
}
