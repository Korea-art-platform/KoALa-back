package com.koala.koalaback.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@Component
public class TossWebhookVerifier {
    private final String expectedToken;

    public TossWebhookVerifier(
            @Value("${toss.webhook-secret:}") String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[Webhook] toss.webhook-secret 미설정 — 토스 웹훅을 전부 거부한다. "
                    + "미확정 결제가 웹훅으로 확정되지 않으므로 운영에서는 반드시 설정할 것");
            this.expectedToken = null;
        } else {
            String raw = webhookSecret + ":";
            this.expectedToken = "Basic " +
                    Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    // 시크릿이 없으면 전부 거부한다. 설정 누락이 곧 인증 없는 결제 확정으로 이어지면 안 된다
    public boolean verify(String authorizationHeader) {
        if (expectedToken == null || authorizationHeader == null) {
            return false;
        }
        // 앞자리부터 한 글자씩 비교하면 응답 시간으로 토큰을 알아낼 수 있다
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                authorizationHeader.getBytes(StandardCharsets.UTF_8));
    }
}
