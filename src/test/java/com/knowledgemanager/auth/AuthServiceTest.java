package com.knowledgemanager.auth;

import com.knowledgemanager.common.dto.LoginResponseDTO;
import com.knowledgemanager.common.dto.UserLoginDTO;
import com.knowledgemanager.common.dto.UserRegisterDTO;
import com.knowledgemanager.common.util.JwtUtil;
import com.knowledgemanager.common.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证服务测试
 */
class AuthServiceTest {

    @Test
    void testPasswordEncryption() {
        // 测试密码加密
        String rawPassword = "test123";
        String encrypted = PasswordUtil.encrypt(rawPassword);
        
        assertNotNull(encrypted);
        assertNotEquals(rawPassword, encrypted);
        assertTrue(PasswordUtil.matches(rawPassword, encrypted));
    }

    @Test
    void testJwtTokenGeneration() {
        // 测试JWT生成
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";
        
        String token = JwtUtil.generateAccessToken(userId, username, role);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        
        // 测试解析
        assertEquals(userId, JwtUtil.getUserId(token));
        assertEquals(username, JwtUtil.getUsername(token));
        assertEquals(role, JwtUtil.getRole(token));
    }

    @Test
    void testJwtExpiration() {
        // 测试Token过期
        Long userId = 1L;
        String username = "testuser";
        String role = "USER";
        
        String token = JwtUtil.generateAccessToken(userId, username, role);
        assertFalse(JwtUtil.isTokenExpired(token));
    }
}
