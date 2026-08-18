package com.koala.koalaback.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 나이스 결제창 인증 결과의 위변조 검증.
 *
 * <h3>이 검증이 곧 인증이다</h3>
 * <p>나이스는 결제창 인증이 끝나면 우리 서버의 returnUrl 로 POST 한다. 이 요청은
 * <b>다른 도메인에서 오는 크로스사이트 POST</b>라 세션 쿠키가 실리지 않는다.
 * 즉 "누가 보냈는지"를 확인할 수단이 서명뿐이다.
 *
 * <p>서명 = {@code hex(sha256(authToken + clientId + amount + secretKey))}.
 * secretKey 는 나이스와 우리만 안다. 서명이 맞으면 나이스가 보낸 것이고,
 * <b>금액이 바뀌지 않았다는 것</b>까지 같이 보장된다 — amount 가 해시 입력에 들어가기 때문이다.
 *
 * <h3>시크릿이 없으면 전부 거부한다</h3>
 * <p>설정 누락이 곧 "아무나 결제를 승인시킬 수 있는 상태"가 되면 안 된다.
 * 토스 웹훅에서 같은 실수(fail-open)가 실제로 있었고 그걸 막은 뒤라, 같은 규칙을 따른다.
 */
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

    /**
     * 결제창이 돌려준 서명을 검증한다.
     *
     * @param authToken 인증 결과로 받은 토큰
     * @param amount    결제창에 넘겼던 금액 (문자열 그대로 — 숫자로 바꾸면 해시가 달라진다)
     * @param signature 나이스가 보낸 서명
     */
    public boolean verify(String authToken, String amount, String signature) {
        if (secretKey.isBlank() || clientKey.isBlank()) return false;
        if (authToken == null || amount == null || signature == null) return false;

        String expected = sha256Hex(authToken + clientKey + amount + secretKey);
        if (expected == null) return false;

        // 앞자리부터 한 글자씩 비교하면 응답 시간으로 서명을 알아낼 수 있다
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
