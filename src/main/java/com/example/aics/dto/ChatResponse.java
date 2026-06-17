package com.example.aics.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatResponse {

    private Long conversationId;
    private String answer;
    private List<SourceChunk> sources = new ArrayList<>();

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceChunk> getSources() {
        return sources;
    }

    public void setSources(List<SourceChunk> sources) {
        this.sources = sources;
    }
}
