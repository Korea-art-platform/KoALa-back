package com.koala.koalaback.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TossWebhookVerifier {
    private final String expectedToken;

    public TossWebhookVerifier(
            @Value("${toss.webhook-secret:}") String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            this.expectedToken = null;
        } else {
            String raw = webhookSecret + ":";
            this.expectedToken = "Basic " +
                    Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    public boolean verify(String authorizationHeader) {
        if (expectedToken == null) {
            return true;
        }
        return expectedToken.equals(authorizationHeader);
    }
}
