package com.koala.koalaback.domain.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentProvider implements PaymentProvider {

    private static final String TOSS_API_BASE = "https://api.tosspayments.com/v1/payments";

    /** 기본값 없음 — 미설정 시 애플리케이션 기동 실패 (운영 환경 미설정 방지) */
    @Value("${toss.secret-key}")
    private String secretKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;  // Spring 관리 빈 주입 (JacksonConfig)
    private final Environment environment;

    /**
     * 기동 시 시크릿 키 유효성 검증.
     * 운영(prod) 프로필에서 테스트 키 사용 시 즉시 기동 실패.
     */
    @PostConstruct
    void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "[Toss] toss.secret-key 가 설정되지 않았습니다. 환경변수 TOSS_SECRET_KEY 를 확인하세요.");
        }
        // TODO: 실제 Toss 운영 키 발급 후 아래 주석 해제
        // boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        // if (isProd && secretKey.startsWith("test_sk_")) {
        //     throw new IllegalStateException(
        //             "[Toss] 운영 환경에 테스트 시크릿 키(test_sk_*)를 사용할 수 없습니다. " +
        //             "TOSS_SECRET_KEY 환경변수에 실제 운영 키를 설정하세요.");
        // }
        if (secretKey.startsWith("test_sk_")) {
            log.warn("[Toss] 테스트 시크릿 키 사용 중 — 운영 배포 전 실제 키로 교체 필요");
        }
    }

    @Override
    public String getProviderCode() { return "TOSS"; }

    @Override
    public PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        try {
            HttpHeaders headers = buildHeaders();
            // Toss API: KRW는 소수점 없는 정수(Long) 필수 — BigDecimal 그대로 전송 시 오류
            Map<String, Object> body = Map.of(
                    "paymentKey", paymentKey,
                    "orderId", orderId,
                    "amount", amount.longValue()
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    TOSS_API_BASE + "/confirm",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<String, Object> res = response.getBody();
            return PaymentConfirmResult.approved(
                    (String) res.get("paymentKey"),
                    (String) res.get("approvalNo"),
                    new BigDecimal(res.get("totalAmount").toString()),
                    toJson(res)
            );
        } catch (HttpClientErrorException e) {
            // 4xx — Toss 가 요청을 명시적으로 거절했다. 승인되지 않은 것이 확실하다.
            String body = e.getResponseBodyAsString();
            String code = extractJsonField(body, "code");
            String msg  = extractJsonField(body, "message");
            log.error("Toss confirm rejected: orderId={}, code={}, message={}", orderId, code, msg);
            return PaymentConfirmResult.rejected(code, msg);
        } catch (HttpServerErrorException e) {
            // 5xx — Toss 내부에서 승인이 완료됐을 수도 있다. 실패로 단정하면 안 된다.
            log.error("Toss confirm 5xx — 승인 여부 미확정: orderId={}, status={}", orderId, e.getStatusCode());
            return PaymentConfirmResult.unknown("TOSS_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            // 타임아웃·연결 실패 — 가장 위험한 케이스. 요청이 전달되어 승인됐을 수 있다.
            log.error("Toss confirm 응답 없음 — 승인 여부 미확정: orderId={}, error={}", orderId, e.getMessage());
            return PaymentConfirmResult.unknown("TOSS_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
            // 분류할 수 없는 오류는 안전한 쪽(미확정)으로 처리한다.
            log.error("Toss confirm error — 승인 여부 미확정: orderId={}, error={}", orderId, e.getMessage());
            return PaymentConfirmResult.unknown("TOSS_ERROR", e.getMessage());
        }
    }

    @Override
    public PaymentLookupResult lookup(String orderId) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    TOSS_API_BASE + "/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class
            );
            Map<String, Object> res = response.getBody();
            if (res == null) {
                return PaymentLookupResult.unavailable();
            }
            String status = (String) res.get("status");
            boolean approved = "DONE".equals(status);
            Object totalAmount = res.get("totalAmount");

            log.info("Toss lookup: orderId={}, status={}", orderId, status);
            return new PaymentLookupResult(
                    true,
                    true,
                    approved,
                    (String) res.get("paymentKey"),
                    (String) res.get("approvalNo"),
                    totalAmount != null ? new BigDecimal(totalAmount.toString()) : null,
                    toJson(res)
            );
        } catch (HttpClientErrorException.NotFound e) {
            // PG 에 결제 자체가 없다 — 승인되지 않은 것이 확실하다.
            log.info("Toss lookup: orderId={} 결제 없음(미승인 확정)", orderId);
            return new PaymentLookupResult(true, false, false, null, null, null, null);
        } catch (Exception e) {
            // 재조회조차 실패 — 여전히 알 수 없다.
            log.error("Toss lookup 실패: orderId={}, error={}", orderId, e.getMessage());
            return PaymentLookupResult.unavailable();
        }
    }

    @Override
    public PaymentCancelResult cancel(String pgTransactionId,
                                      BigDecimal cancelAmount, String reason) {
        try {
            HttpHeaders headers = buildHeaders();
            Map<String, Object> body = Map.of(
                    "cancelReason", reason,
                    "cancelAmount", cancelAmount
            );
            ResponseEntity<Map> response = restTemplate.exchange(
                    TOSS_API_BASE + "/" + pgTransactionId + "/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<String, Object> res = response.getBody();
            return PaymentCancelResult.cancelled(cancelAmount, toJson(res));
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            String code = extractJsonField(body, "code");
            String msg  = extractJsonField(body, "message");
            log.error("Toss cancel rejected: pgTransactionId={}, code={}, message={}", pgTransactionId, code, msg);
            return PaymentCancelResult.rejected(code, msg);
        } catch (HttpServerErrorException e) {
            log.error("Toss cancel 5xx — 취소 여부 미확정: pgTransactionId={}, status={}",
                    pgTransactionId, e.getStatusCode());
            return PaymentCancelResult.unknown("TOSS_CANCEL_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Toss cancel 응답 없음 — 취소 여부 미확정: pgTransactionId={}, error={}",
                    pgTransactionId, e.getMessage());
            return PaymentCancelResult.unknown("TOSS_CANCEL_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
            log.error("Toss cancel error — 취소 여부 미확정: pgTransactionId={}, error={}",
                    pgTransactionId, e.getMessage());
            return PaymentCancelResult.unknown("TOSS_CANCEL_ERROR", e.getMessage());
        }
    }

    /** Map → 유효한 JSON 문자열 변환 */
    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize response to JSON", e);
            return "{}";
        }
    }

    /** Toss 에러 응답 JSON에서 특정 필드 값 추출 */
    private String extractJsonField(String json, String field) {
        if (json == null || json.isBlank()) return null;
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private HttpHeaders buildHeaders() {
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }
}