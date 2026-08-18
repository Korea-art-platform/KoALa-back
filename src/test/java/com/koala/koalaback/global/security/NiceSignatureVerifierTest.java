package com.koala.koalaback.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("나이스 서명 검증")
class NiceSignatureVerifierTest {
    private static final String CLIENT_KEY = "S2_test_client";
    private static final String SECRET_KEY = "test_secret_value";

    private final NiceSignatureVerifier verifier =
            new NiceSignatureVerifier(CLIENT_KEY, SECRET_KEY);

    private String sign(String authToken, String amount, String secret) {
        return hex(sha256(authToken + CLIENT_KEY + amount + secret));
    }

    @Test
    @DisplayName("올바른 서명은 통과한다")
    void validSignaturePasses() {
        assertThat(verifier.verify("tok_1", "150000", sign("tok_1", "150000", SECRET_KEY)))
                .isTrue();
    }

    @Test
    @DisplayName("금액을 바꾸면 서명이 깨진다 — 금액이 해시 입력에 들어간다")
    void amountTamperingIsCaught() {
        String signature = sign("tok_1", "150000", SECRET_KEY);

        assertThat(verifier.verify("tok_1", "1000", signature)).isFalse();
    }

    @Test
    @DisplayName("토큰을 바꾸면 서명이 깨진다")
    void tokenTamperingIsCaught() {
        String signature = sign("tok_1", "150000", SECRET_KEY);

        assertThat(verifier.verify("tok_other", "150000", signature)).isFalse();
    }

    @Test
    @DisplayName("시크릿을 모르면 서명을 만들 수 없다")
    void wrongSecretFails() {
        assertThat(verifier.verify("tok_1", "150000", sign("tok_1", "150000", "guessed")))
                .isFalse();
    }

    @Test
    @DisplayName("시크릿이 없으면 전부 거부한다 — 설정 누락이 무인증 승인이 되면 안 된다")
    void missingSecretRejectsEverything() {
        NiceSignatureVerifier noSecret = new NiceSignatureVerifier(CLIENT_KEY, "");

        assertThat(noSecret.verify("tok_1", "150000", sign("tok_1", "150000", SECRET_KEY))).isFalse();
        assertThat(noSecret.verify("tok_1", "150000", "anything")).isFalse();
    }

    @Test
    @DisplayName("클라이언트 키가 없어도 전부 거부한다")
    void missingClientKeyRejectsEverything() {
        assertThat(new NiceSignatureVerifier("", SECRET_KEY).verify("t", "1", "sig")).isFalse();
    }

    @Test
    @DisplayName("값이 비어 있으면 거부한다")
    void nullsRejected() {
        String signature = sign("tok_1", "150000", SECRET_KEY);

        assertThat(verifier.verify(null, "150000", signature)).isFalse();
        assertThat(verifier.verify("tok_1", null, signature)).isFalse();
        assertThat(verifier.verify("tok_1", "150000", null)).isFalse();
    }

    @Test
    @DisplayName("대문자로 와도 통과한다 — 표기만 다르지 같은 값이다")
    void upperCaseSignatureAccepted() {
        String signature = sign("tok_1", "150000", SECRET_KEY).toUpperCase();

        assertThat(verifier.verify("tok_1", "150000", signature)).isTrue();
    }

    @Test
    @DisplayName("앞뒤 공백은 무시한다")
    void surroundingWhitespaceIgnored() {
        String signature = "  " + sign("tok_1", "150000", SECRET_KEY) + "  ";

        assertThat(verifier.verify("tok_1", "150000", signature)).isTrue();
    }

    @Test
    @DisplayName("금액은 문자열 그대로 쓴다 — 150000 과 150000.00 은 다른 서명이다")
    void amountIsComparedAsGivenString() {
        String signature = sign("tok_1", "150000", SECRET_KEY);

        assertThat(verifier.verify("tok_1", "150000", signature)).isTrue();
        assertThat(verifier.verify("tok_1", "150000.00", signature)).isFalse();
    }

    private String signWebhook(String tid, String amount, String ediDate, String secret) {
        return hex(sha256(tid + amount + ediDate + secret));
    }

    @Test
    @DisplayName("웹훅: 올바른 서명은 통과한다")
    void webhookValidSignaturePasses() {
        String ediDate = "2026-08-18T14:00:00.000+0900";

        assertThat(verifier.verifyWebhook("tid_1", "150000", ediDate,
                signWebhook("tid_1", "150000", ediDate, SECRET_KEY))).isTrue();
    }

    @Test
    @DisplayName("웹훅: 금액을 바꾸면 서명이 깨진다")
    void webhookAmountTamperingIsCaught() {
        String ediDate = "2026-08-18T14:00:00.000+0900";
        String signature = signWebhook("tid_1", "150000", ediDate, SECRET_KEY);

        assertThat(verifier.verifyWebhook("tid_1", "1000", ediDate, signature)).isFalse();
    }

    @Test
    @DisplayName("웹훅: 같은 전문을 결제창 규칙으로 서명하면 통과하지 못한다 — 두 규칙은 다르다")
    void webhookDoesNotAcceptWindowSignature() {
        String ediDate = "2026-08-18T14:00:00.000+0900";
        String windowStyle = sign("tid_1", "150000", SECRET_KEY);

        assertThat(verifier.verifyWebhook("tid_1", "150000", ediDate, windowStyle)).isFalse();
    }

    @Test
    @DisplayName("웹훅: 전문 시각을 바꾸면 서명이 깨진다 — 지난 전문을 재사용할 수 없다")
    void webhookEdiDateTamperingIsCaught() {
        String signature = signWebhook("tid_1", "150000", "2026-08-18T14:00:00.000+0900", SECRET_KEY);

        assertThat(verifier.verifyWebhook("tid_1", "150000",
                "2026-08-19T14:00:00.000+0900", signature)).isFalse();
    }

    @Test
    @DisplayName("웹훅: 시크릿이 없으면 전부 거부한다")
    void webhookMissingSecretRejectsEverything() {
        NiceSignatureVerifier noSecret = new NiceSignatureVerifier(CLIENT_KEY, "");
        String ediDate = "2026-08-18T14:00:00.000+0900";

        assertThat(noSecret.verifyWebhook("tid_1", "150000", ediDate,
                signWebhook("tid_1", "150000", ediDate, SECRET_KEY))).isFalse();
    }

    @Test
    @DisplayName("웹훅: 클라이언트 키가 없어도 검증된다 — 웹훅 서명은 clientId 를 쓰지 않는다")
    void webhookWorksWithoutClientKey() {
        NiceSignatureVerifier noClientKey = new NiceSignatureVerifier("", SECRET_KEY);
        String ediDate = "2026-08-18T14:00:00.000+0900";

        assertThat(noClientKey.verifyWebhook("tid_1", "150000", ediDate,
                signWebhook("tid_1", "150000", ediDate, SECRET_KEY))).isTrue();
    }

    @Test
    @DisplayName("웹훅: 값이 비어 있으면 거부한다")
    void webhookNullsRejected() {
        String ediDate = "2026-08-18T14:00:00.000+0900";
        String signature = signWebhook("tid_1", "150000", ediDate, SECRET_KEY);

        assertThat(verifier.verifyWebhook(null, "150000", ediDate, signature)).isFalse();
        assertThat(verifier.verifyWebhook("tid_1", null, ediDate, signature)).isFalse();
        assertThat(verifier.verifyWebhook("tid_1", "150000", null, signature)).isFalse();
        assertThat(verifier.verifyWebhook("tid_1", "150000", ediDate, null)).isFalse();
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
