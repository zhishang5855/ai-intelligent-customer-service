package com.example.aics.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.aics.dto.AuthLoginRequest;
import com.example.aics.dto.AuthLoginResponse;
import com.example.aics.entity.UserAccount;
import com.example.aics.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String TEST_PASSWORD_HASH =
            "0abf9601b8ad64ec7c88814ecb7b7ee6da1b54e74aaf6393c4e8ac37a91f2489";

    @Test
    void loginReturnsUserInfoAndRandomTokenWhenPasswordMatches() {
        AuthService authService = new AuthService(mapperReturning(activeTestUser()));

        AuthLoginResponse response = authService.login(request(" test ", "123456"));

        assertEquals(1L, response.getUserId());
        assertEquals("test", response.getUsername());
        assertEquals("测试用户", response.getNickname());
        assertNotNull(response.getToken());
        assertEquals(43, response.getToken().length());
        assertNotNull(authService.validateToken(response.getToken()));
    }

    @Test
    void loginRejectsMissingUser() {
        AuthService authService = new AuthService(mapperReturning(null));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.login(request("missing", "123456")));

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    void loginRejectsDisabledUser() {
        UserAccount user = activeTestUser();
        user.setStatus("DISABLED");
        AuthService authService = new AuthService(mapperReturning(user));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.login(request("test", "123456")));

        assertEquals("用户已被禁用", exception.getMessage());
    }

    @Test
    void loginRejectsWrongPassword() {
        AuthService authService = new AuthService(mapperReturning(activeTestUser()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> authService.login(request("test", "wrong")));

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    private UserAccountMapper mapperReturning(UserAccount user) {
        UserAccountMapper mapper = mock(UserAccountMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(user);
        return mapper;
    }

    private UserAccount activeTestUser() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("test");
        user.setPasswordHash(TEST_PASSWORD_HASH);
        user.setNickname("测试用户");
        user.setStatus("ACTIVE");
        return user;
    }

    private AuthLoginRequest request(String username, String password) {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
