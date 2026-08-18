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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentProvider implements PaymentProvider {
    private static final String TOSS_API_BASE = "https://api.tosspayments.com/v1/payments";

    @Value("${toss.secret-key}")
    private String secretKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @PostConstruct
    void validateSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "[Toss] toss.secret-key 가 설정되지 않았습니다. 환경변수 TOSS_SECRET_KEY 를 확인하세요.");
        }
        if (secretKey.startsWith("test_sk_")) {
            boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (isProd) {
                log.error("[Toss] ★운영 환경에서 테스트 시크릿 키 사용 중★ — 실결제가 이루어지지 않는다");
            } else {
                log.warn("[Toss] 테스트 시크릿 키 사용 중 — 운영 배포 전 실제 키로 교체 필요");
            }
        }
    }

    @Override
    public String getProviderCode() { return "TOSS"; }

    @Override
    public PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        try {
            HttpHeaders headers = buildHeaders();

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
            String body = e.getResponseBodyAsString();
            String code = extractJsonField(body, "code");
            String msg  = extractJsonField(body, "message");
            log.error("Toss confirm rejected: orderId={}, code={}, message={}", orderId, code, msg);
            return PaymentConfirmResult.rejected(code, msg);
        } catch (HttpServerErrorException e) {
            log.error("Toss confirm 5xx — 승인 여부 미확정: orderId={}, status={}", orderId, e.getStatusCode());
            return PaymentConfirmResult.unknown("TOSS_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Toss confirm 응답 없음 — 승인 여부 미확정: orderId={}, error={}", orderId, e.getMessage());
            return PaymentConfirmResult.unknown("TOSS_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
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
            log.info("Toss lookup: orderId={} 결제 없음(미승인 확정)", orderId);
            return new PaymentLookupResult(true, false, false, null, null, null, null);
        } catch (Exception e) {
            log.error("Toss lookup 실패: orderId={}, error={}", orderId, e.getMessage());
            return PaymentLookupResult.unavailable();
        }
    }

    @Override
    public PaymentCancelResult cancel(String pgTransactionId,
                                      BigDecimal cancelAmount, String reason) {
        try {
            HttpHeaders headers = buildHeaders();
            // Map.of 는 값이 null 이면 던진다. 전액 취소는 금액을 빼고 보내야 하므로 쓸 수 없다.
            // 토스도 cancelAmount 가 없으면 전액 취소로 읽는다
            Map<String, Object> body = new HashMap<>();
            body.put("cancelReason", reason);
            if (cancelAmount != null) {
                body.put("cancelAmount", cancelAmount);
            }
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

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize response to JSON", e);
            return "{}";
        }
    }

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
