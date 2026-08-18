package com.koala.koalaback.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 나이스 결제창 인증 결과 서명 검증.
 *
 * <p>이 검증이 뚫리면 <b>아무나 우리 서버에 "결제 승인됐다"고 알릴 수 있다.</b>
 * 세션 쿠키가 실리지 않는 크로스사이트 POST 라 다른 방어 수단이 없다.
 */
@DisplayName("나이스 서명 검증")
class NiceSignatureVerifierTest {

    private static final String CLIENT_KEY = "S2_test_client";
    private static final String SECRET_KEY = "test_secret_value";

    private final NiceSignatureVerifier verifier =
            new NiceSignatureVerifier(CLIENT_KEY, SECRET_KEY);

    /** 나이스가 만드는 것과 같은 방식으로 서명을 계산한다 */
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
