package com.example.aics.common;

public class AuthenticatedUser {

    private final Long userId;
    private final String username;
    private final String nickname;

    public AuthenticatedUser(Long userId, String username, String nickname) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }
}
