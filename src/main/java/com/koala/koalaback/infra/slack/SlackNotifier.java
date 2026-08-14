package com.koala.koalaback.infra.slack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "koala.slack.enabled", havingValue = "true")
public class SlackNotifier {
    private final RestTemplate restTemplate;
    private final String webhookUrl;

    public SlackNotifier(@Value("${koala.slack.webhook-url:}") String webhookUrl,
                         @Value("${koala.slack.timeout-ms:3000}") long timeoutMs) {
        this.webhookUrl = webhookUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restTemplate = new RestTemplate(factory);

        if (webhookUrl.isBlank()) {
            log.warn("koala.slack.enabled=true 이지만 webhook-url 이 비어 있다 — 슬랙 알림이 나가지 않는다");
        }
    }

    @Async
    public void send(String text) {
        if (webhookUrl.isBlank()) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(
                    webhookUrl,
                    new HttpEntity<>(Map.of("text", text), headers),
                    String.class);
        } catch (Exception e) {
            log.warn("슬랙 알림 발송 실패: {}", e.getMessage());
        }
    }
}
