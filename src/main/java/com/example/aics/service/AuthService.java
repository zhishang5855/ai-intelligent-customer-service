package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aics.dto.AuthLoginRequest;
import com.example.aics.dto.AuthLoginResponse;
import com.example.aics.entity.UserAccount;
import com.example.aics.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final UserAccountMapper userAccountMapper;

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
            throw new IllegalArgumentException("用户不存在");
        }
        if (!ACTIVE_STATUS.equals(user.getStatus())) {
            throw new IllegalArgumentException("用户已被禁用");
        }
        if (!hashPassword(username, password).equalsIgnoreCase(user.getPasswordHash())) {
            throw new IllegalArgumentException("密码错误");
        }

        AuthLoginResponse response = new AuthLoginResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setToken(buildSimpleToken(user));
        return response;
    }

    private String buildSimpleToken(UserAccount user) {
        return "simple-token-" + user.getId() + "-" + Instant.now().toEpochMilli();
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
}
