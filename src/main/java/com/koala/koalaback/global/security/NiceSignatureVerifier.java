package com.koala.koalaback.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class NiceSignatureVerifier {
    private final String clientKey;
    private final String secretKey;

    public NiceSignatureVerifier(@Value("${nicepay.client-key:}") String clientKey,
                                 @Value("${nicepay.secret-key:}") String secretKey) {
        this.clientKey = clientKey;
        this.secretKey = secretKey;

        if (clientKey.isBlank() || secretKey.isBlank()) {
            log.warn("[NicePay] client-key / secret-key 미설정 — 결제창 인증 결과를 전부 거부한다. "
                    + "운영에서는 반드시 설정할 것");
        }
    }

    public boolean verify(String authToken, String amount, String signature) {
        if (secretKey.isBlank() || clientKey.isBlank()) return false;
        if (authToken == null || amount == null || signature == null) return false;

        String expected = sha256Hex(authToken + clientKey + amount + secretKey);
        if (expected == null) return false;

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public boolean verifyWebhook(String tid, String amount, String ediDate, String signature) {
        if (secretKey.isBlank()) return false;
        if (tid == null || amount == null || ediDate == null || signature == null) return false;

        String expected = sha256Hex(tid + amount + ediDate + secretKey);
        if (expected == null) return false;

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 을 쓸 수 없다", e);
            return null;
        }
    }
}
