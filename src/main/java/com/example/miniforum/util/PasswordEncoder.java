package com.example.miniforum.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码加密工具类
 * 采用 SHA-256 + 随机盐 的哈希方式对密码进行加密存储，
 * 避免明文密码落库导致的安全风险。
 */
public final class PasswordEncoder {

    /** 哈希算法 */
    private static final String ALGORITHM = "SHA-256";
    /** 盐的字节长度 */
    private static final int SALT_LENGTH = 16;
    /** 盐与哈希值之间的分隔符 */
    private static final String SEPARATOR = "$";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordEncoder() {
        // 工具类禁止实例化
    }

    /**
     * 对明文密码进行加盐哈希，返回格式：盐$哈希值
     *
     * @param rawPassword 明文密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = hash(rawPassword, salt);
        return Base64.getEncoder().encodeToString(salt) + SEPARATOR + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 校验明文密码与加密后的密码是否匹配
     *
     * @param rawPassword     明文密码
     * @param encodedPassword 加密后的密码（盐$哈希值）
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\" + SEPARATOR);
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] hash = hash(rawPassword, salt);
        return MessageDigest.isEqual(hash, Base64.getDecoder().decode(parts[1]));
    }

    /**
     * 使用 SHA-256 对密码加盐后计算哈希
     */
    private static byte[] hash(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.update(salt);
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("系统不支持 SHA-256 算法", e);
        }
    }
}
