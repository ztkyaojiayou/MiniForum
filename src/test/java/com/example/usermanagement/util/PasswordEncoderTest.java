package com.example.usermanagement.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码加密工具单元测试
 */
class PasswordEncoderTest {

    @Test
    void encode_shouldProduceSaltedHash() {
        String encoded = PasswordEncoder.encode("secret123");
        // 加密结果不应等于明文
        assertNotEquals("secret123", encoded);
        // 应包含分隔符（盐$哈希）
        assertTrue(encoded.contains("$"));
    }

    @Test
    void encode_samePasswordShouldProduceDifferentHash() {
        String first = PasswordEncoder.encode("secret123");
        String second = PasswordEncoder.encode("secret123");
        // 随机盐，两次加密结果应不同
        assertNotEquals(first, second);
    }

    @Test
    void matches_shouldReturnTrueForCorrectPassword() {
        String encoded = PasswordEncoder.encode("secret123");
        assertTrue(PasswordEncoder.matches("secret123", encoded));
    }

    @Test
    void matches_shouldReturnFalseForWrongPassword() {
        String encoded = PasswordEncoder.encode("secret123");
        assertFalse(PasswordEncoder.matches("wrong", encoded));
    }

    @Test
    void matches_shouldHandleNullInput() {
        assertFalse(PasswordEncoder.matches(null, "salted"));
        assertFalse(PasswordEncoder.matches("pwd", null));
        assertFalse(PasswordEncoder.matches(null, null));
    }
}
