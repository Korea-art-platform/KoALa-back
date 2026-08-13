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

/**
 * 스윗트래커 운송장 조회.
 *
 * <p>키가 없으면 이 빈이 뜨지 않는다({@code koala.delivery.tracking.enabled=false} 기본).
 * 그 상태에서는 스케줄러도 돌지 않고, 배송완료는 지금처럼 어드민에서 손으로 누르면 된다.
 *
 * <h3>응답 해석</h3>
 * <p>{@code level} 이 배송 단계이고 <b>6이 배송완료</b>다. {@code complete} 플래그도 같이 오는데,
 * 둘 중 하나만 봐도 되지만 업체가 한쪽 필드를 바꾸는 일이 실제로 있어 둘 다 본다.
 *
 * <p>조회 실패는 전부 {@code UNKNOWN} 이다. 여기서 예외를 던지면 스케줄러가 한 건 때문에
 * 나머지 주문을 못 본다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "koala.delivery.tracking.enabled", havingValue = "true")
public class SweetTrackerDeliveryTracker implements DeliveryTracker {

    private static final String ENDPOINT = "http://info.sweettracker.co.kr/api/v1/trackingInfo";
    /** 배송완료 단계 */
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

        // 목록에 없는 택배사(예전 자유 입력 값)는 조회할 방법이 없다
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
            // 운송장 번호는 남기되 키는 절대 남기지 않는다
            log.warn("운송장 조회 실패: carrier={}, trackingNo={}, error={}",
                    carrierCode, trackingNo, e.getMessage());
            return Status.UNKNOWN;
        }
    }

    /** 파싱 실패도 UNKNOWN — 응답 형식이 바뀌었다고 배송 상태를 단정하면 안 된다 */
    Status parse(String body) {
        if (body == null || body.isBlank()) return Status.UNKNOWN;

        try {
            JsonNode root = objectMapper.readTree(body);

            // 미등록 운송장·키 오류 등은 status=false 로 온다
            if (root.path("status").isBoolean() && !root.path("status").asBoolean()) {
                return Status.UNKNOWN;
            }

            if (root.path("complete").asBoolean(false)
                    || root.path("level").asInt(0) >= LEVEL_DELIVERED) {
                return Status.DELIVERED;
            }

            // level 이 있으면 배송 중, 아예 없으면 판단 불가
            return root.hasNonNull("level") ? Status.IN_TRANSIT : Status.UNKNOWN;

        } catch (Exception e) {
            log.warn("운송장 조회 응답 파싱 실패: {}", e.getMessage());
            return Status.UNKNOWN;
        }
    }
}
