package com.koala.koalaback.infra.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "koala.delivery.tracking.enabled", havingValue = "true")
public class SweetTrackerDeliveryTracker implements DeliveryTracker {
    private static final String ENDPOINT = "http://info.sweettracker.co.kr/api/v1/trackingInfo";

    private static final int LEVEL_DELIVERED = 6;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public SweetTrackerDeliveryTracker(
            ObjectMapper objectMapper,
            @Value("${koala.delivery.tracking.api-key:}") String apiKey,
            @Value("${koala.delivery.tracking.timeout-ms:5000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restTemplate = new RestTemplate(factory);

        if (apiKey.isBlank()) {
            log.warn("koala.delivery.tracking.enabled=true 이지만 api-key 가 비어 있다 — 배송 자동 추적이 동작하지 않는다");
        }
    }

    @Override
    public Status track(String carrierCode, String trackingNo) {
        if (apiKey.isBlank() || trackingNo == null || trackingNo.isBlank()) return Status.UNKNOWN;

        if (Carrier.fromCode(carrierCode).isEmpty()) return Status.UNKNOWN;

        try {
            String url = UriComponentsBuilder.fromUriString(ENDPOINT)
                    .queryParam("t_key", apiKey)
                    .queryParam("t_code", carrierCode)
                    .queryParam("t_invoice", trackingNo)
                    .toUriString();

            String body = restTemplate.getForObject(url, String.class);
            return parse(body);
        } catch (Exception e) {
            log.warn("운송장 조회 실패: carrier={}, trackingNo={}, error={}",
                    carrierCode, trackingNo, e.getMessage());
            return Status.UNKNOWN;
        }
    }

    Status parse(String body) {
        if (body == null || body.isBlank()) return Status.UNKNOWN;

        try {
            JsonNode root = objectMapper.readTree(body);

            if (root.path("status").isBoolean() && !root.path("status").asBoolean()) {
                return Status.UNKNOWN;
            }

            if (root.path("complete").asBoolean(false)
                    || root.path("level").asInt(0) >= LEVEL_DELIVERED) {
                return Status.DELIVERED;
            }

            return root.hasNonNull("level") ? Status.IN_TRANSIT : Status.UNKNOWN;
        } catch (Exception e) {
            log.warn("운송장 조회 응답 파싱 실패: {}", e.getMessage());
            return Status.UNKNOWN;
        }
    }
}
