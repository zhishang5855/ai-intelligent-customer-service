package com.example.aics.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("chat_message")
public class ChatMessage extends BaseEntity {

    @TableId
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String ragSourcesJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRagSourcesJson() {
        return ragSourcesJson;
    }

    public void setRagSourcesJson(String ragSourcesJson) {
        this.ragSourcesJson = ragSourcesJson;
    }
}
