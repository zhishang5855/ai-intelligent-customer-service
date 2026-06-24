package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aics.common.AuthenticatedUser;
import com.example.aics.dto.AuthLoginRequest;
import com.example.aics.dto.AuthLoginResponse;
import com.example.aics.entity.UserAccount;
import com.example.aics.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String LOGIN_FAILED_MESSAGE = "用户名或密码错误";
    private static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final UserAccountMapper userAccountMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TokenSession> sessions = new ConcurrentHashMap<>();

    public AuthService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        String username = request.getUsername().trim();
        String password = request.getPassword();
        UserAccount user = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
                .last("limit 1"));
        if (user == null) {
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }
        if (!ACTIVE_STATUS.equals(user.getStatus())) {
            throw new IllegalArgumentException("用户已被禁用");
        }
        if (!hashPassword(username, password).equalsIgnoreCase(user.getPasswordHash())) {
            throw new IllegalArgumentException(LOGIN_FAILED_MESSAGE);
        }

        AuthLoginResponse response = new AuthLoginResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setToken(createToken(user));
        return response;
    }

    public AuthenticatedUser validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenSession session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return null;
        }
        return session.user();
    }

    private String createToken(UserAccount user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.getId(), user.getUsername(), user.getNickname());
        sessions.put(token, new TokenSession(authenticatedUser, LocalDateTime.now().plus(TOKEN_TTL)));
        return token;
    }

    private String hashPassword(String username, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    private record TokenSession(AuthenticatedUser user, LocalDateTime expiresAt) {
    }
}
