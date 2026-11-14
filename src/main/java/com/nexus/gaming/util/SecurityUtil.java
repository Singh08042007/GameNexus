package com.nexus.gaming.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic and identifier helper utilities for password hashing,
 * session tokens, and collision-resistant Room Code generation.
 */
public final class SecurityUtil {

    private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HASH_SALT = "NEXUS_GAMING_HUB_V1_SALT";

    private SecurityUtil() {
    }

    public static String hashPassword(String rawPassword) {
        if (rawPassword == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((HASH_SALT + ":" + rawPassword).getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xFF;
                hex[i * 2] = HEX_CHARS[v >>> 4];
                hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyPassword(String rawPassword, String expectedHash) {
        if (rawPassword == null || expectedHash == null) return false;
        return hashPassword(rawPassword).equals(expectedHash);
    }

    public static String generateRoomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = SECURE_RANDOM.nextInt(ROOM_CODE_ALPHABET.length());
            sb.append(ROOM_CODE_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    public static String generateSessionToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return "nxs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
