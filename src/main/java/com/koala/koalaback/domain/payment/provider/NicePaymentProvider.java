package com.koala.koalaback.domain.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 나이스페이먼츠 결제 연동.
 *
 * <h3>토스와 승인 흐름이 다르다</h3>
 * <p>토스는 브라우저가 successUrl 로 돌아온 뒤 <b>프론트가</b> 승인 API 를 부른다.
 * 나이스는 결제창 인증이 끝나면 나이스가 <b>서버의 returnUrl 로 POST</b> 하고,
 * 그 요청을 받은 서버가 승인 API 를 부른다. 그래서 승인 진입점이 컨트롤러 하나 더 붙는다.
 *
 * <p>다만 이 클래스가 하는 일은 토스판과 같다 — {@code confirm} 은 tid 로 승인을 요청하고,
 * 결과를 {@code SUCCEEDED / REJECTED / UNKNOWN} 셋 중 하나로 번역한다.
 * 그 위의 주문·재고·보상취소 로직은 PG 를 모른다.
 *
 * <h3>UNKNOWN 을 REJECTED 로 만들지 않는다</h3>
 * <p>5xx·타임아웃은 "승인이 안 됐다"가 아니라 "승인됐는지 모른다"다. 실패로 단정하면
 * 고객 돈만 빠진 채 주문이 취소된다. 토스판과 같은 규칙을 그대로 따른다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nicepay.enabled", havingValue = "true")
public class NicePaymentProvider implements PaymentProvider {

    /** 성공 판정 코드. 나이스는 resultCode 0000 이 성공이다 */
    private static final String RESULT_SUCCESS = "0000";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private final String apiBase;
    private final String clientKey;
    private final String secretKey;

    public NicePaymentProvider(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               Environment environment,
                               @Value("${nicepay.api-base:https://api.nicepay.co.kr/v1}") String apiBase,
                               @Value("${nicepay.client-key:}") String clientKey,
                               @Value("${nicepay.secret-key:}") String secretKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.clientKey = clientKey;
        this.secretKey = secretKey;
    }

    @PostConstruct
    void validateKeys() {
        if (clientKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "[NicePay] nicepay.client-key / nicepay.secret-key 가 설정되지 않았습니다. "
                            + "환경변수 NICEPAY_CLIENT_KEY, NICEPAY_SECRET_KEY 를 확인하세요.");
        }
        boolean sandbox = apiBase.contains("sandbox");
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (sandbox && prod) {
            log.error("[NicePay] ★운영 환경에서 샌드박스 주소 사용 중★ — 실결제가 이루어지지 않는다");
        } else if (sandbox) {
            log.warn("[NicePay] 샌드박스 사용 중 — 운영 배포 전 실제 주소·키로 교체 필요");
        }
    }

    @Override
    public String getProviderCode() { return "NICEPAY"; }

    /**
     * 승인.
     *
     * <p>{@code paymentKey} 는 나이스의 tid 다. 결제창 인증 결과로 받은 값을 그대로 쓴다.
     * 금액을 함께 보내 나이스가 인증 시점 금액과 대조하게 한다 — 우리가 보낸 금액이
     * 다르면 나이스가 거절한다. 위변조 방어의 마지막 단계다.
     */
    @Override
    public PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase + "/payments/" + paymentKey,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("amount", amount.longValue()), buildHeaders()),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null) {
                return PaymentConfirmResult.unknown("NICE_EMPTY_BODY", "응답 본문이 비어 있습니다.");
            }

            String resultCode = str(res.get("resultCode"));
            String status = str(res.get("status"));

            // 나이스는 200 을 주면서 본문 코드로 실패를 알린다. HTTP 상태만 보면 안 된다.
            if (!RESULT_SUCCESS.equals(resultCode) || !"paid".equals(status)) {
                log.error("NicePay confirm rejected: orderId={}, resultCode={}, status={}, msg={}",
                        orderId, resultCode, status, str(res.get("resultMsg")));
                return PaymentConfirmResult.rejected(resultCode, str(res.get("resultMsg")));
            }

            return PaymentConfirmResult.approved(
                    str(res.get("tid")),
                    str(res.get("approveNo")),
                    res.get("amount") != null ? new BigDecimal(res.get("amount").toString()) : amount,
                    toJson(res));

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            String code = extractJsonField(body, "resultCode");
            String msg = extractJsonField(body, "resultMsg");
            log.error("NicePay confirm 4xx: orderId={}, code={}, message={}", orderId, code, msg);
            return PaymentConfirmResult.rejected(code, msg);
        } catch (HttpServerErrorException e) {
            log.error("NicePay confirm 5xx — 승인 여부 미확정: orderId={}, status={}", orderId, e.getStatusCode());
            return PaymentConfirmResult.unknown("NICE_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("NicePay confirm 응답 없음 — 승인 여부 미확정: orderId={}, error={}", orderId, e.getMessage());
            return PaymentConfirmResult.unknown("NICE_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
            log.error("NicePay confirm error — 승인 여부 미확정: orderId={}, error={}", orderId, e.getMessage());
            return PaymentConfirmResult.unknown("NICE_ERROR", e.getMessage());
        }
    }

    /**
     * 주문번호로 재조회 — 미확정(IN_DOUBT) 을 푸는 경로다.
     *
     * <p>나이스의 주문번호 조회는 <b>주문일자(orderDate)를 함께 요구</b>한다. 인터페이스에는
     * 날짜가 없어 오늘(KST)로 먼저 찾고, 없으면 어제로 한 번 더 본다.
     * 재조회는 승인 시도 직후(수초~수분)에 일어나므로 이 두 날짜면 자정 경계까지 덮는다.
     *
     * <p>조회 자체가 실패하면 {@code unavailable()} 이다. "결제 없음"으로 단정하면
     * 실제로는 승인된 건을 실패 처리해 고객 돈만 빠진다.
     */
    @Override
    public PaymentLookupResult lookup(String orderId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        PaymentLookupResult result = lookupOn(orderId, today);
        if (result.queried() && !result.found()) {
            // 자정 직후라면 주문일이 어제일 수 있다
            PaymentLookupResult yesterday = lookupOn(orderId, today.minusDays(1));
            if (yesterday.found()) return yesterday;
        }
        return result;
    }

    private PaymentLookupResult lookupOn(String orderId, LocalDate orderDate) {
        String date = orderDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase + "/payments/find/" + orderId + "?orderDate=" + date,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null) return PaymentLookupResult.unavailable();

            String resultCode = str(res.get("resultCode"));
            String status = str(res.get("status"));

            if (!RESULT_SUCCESS.equals(resultCode)) {
                // 조회는 됐는데 해당 거래가 없다 — 미승인으로 확정할 수 있는 유일한 경우
                log.info("NicePay lookup: orderId={}, date={} 거래 없음 (code={})", orderId, date, resultCode);
                return new PaymentLookupResult(true, false, false, null, null, null, null);
            }

            boolean approved = "paid".equals(status);
            log.info("NicePay lookup: orderId={}, date={}, status={}", orderId, date, status);

            return new PaymentLookupResult(
                    true, true, approved,
                    str(res.get("tid")),
                    str(res.get("approveNo")),
                    res.get("amount") != null ? new BigDecimal(res.get("amount").toString()) : null,
                    toJson(res));

        } catch (HttpClientErrorException.NotFound e) {
            log.info("NicePay lookup: orderId={}, date={} 결제 없음(미승인 확정)", orderId, date);
            return new PaymentLookupResult(true, false, false, null, null, null, null);
        } catch (Exception e) {
            log.error("NicePay lookup 실패: orderId={}, date={}, error={}", orderId, date, e.getMessage());
            return PaymentLookupResult.unavailable();
        }
    }

    /**
     * 취소(환불).
     *
     * <p>{@code cancelAmt} 를 빼면 전액 취소, 넣으면 부분 취소다.
     * {@code orderId} 는 나이스가 필수로 요구하는데, 우리 인터페이스에는 tid 만 넘어온다.
     * 부분취소 중복 방지에 쓰이는 값이라 tid 를 그대로 넣지 않고
     * <b>매 호출마다 다른 값</b>을 만들어 넣는다 — 같은 결제를 여러 번 부분취소할 수 있어야 한다.
     */
    @Override
    public PaymentCancelResult cancel(String pgTransactionId, BigDecimal cancelAmount, String reason) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("reason", trim(reason, 100));
            body.put("orderId", cancelRequestId(pgTransactionId));
            if (cancelAmount != null) {
                body.put("cancelAmt", cancelAmount.longValue());
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase + "/payments/" + pgTransactionId + "/cancel",
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders()),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null) {
                return PaymentCancelResult.unknown("NICE_CANCEL_EMPTY_BODY", "응답 본문이 비어 있습니다.");
            }

            String resultCode = str(res.get("resultCode"));
            String status = str(res.get("status"));
            boolean cancelled = RESULT_SUCCESS.equals(resultCode)
                    && ("cancelled".equals(status) || "partialCancelled".equals(status));

            if (!cancelled) {
                log.error("NicePay cancel rejected: tid={}, resultCode={}, status={}, msg={}",
                        pgTransactionId, resultCode, status, str(res.get("resultMsg")));
                return PaymentCancelResult.rejected(resultCode, str(res.get("resultMsg")));
            }
            return PaymentCancelResult.cancelled(cancelAmount, toJson(res));

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("NicePay cancel 4xx: tid={}, body={}", pgTransactionId, body);
            return PaymentCancelResult.rejected(
                    extractJsonField(body, "resultCode"), extractJsonField(body, "resultMsg"));
        } catch (HttpServerErrorException e) {
            log.error("NicePay cancel 5xx — 취소 여부 미확정: tid={}, status={}",
                    pgTransactionId, e.getStatusCode());
            return PaymentCancelResult.unknown("NICE_CANCEL_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("NicePay cancel 응답 없음 — 취소 여부 미확정: tid={}, error={}",
                    pgTransactionId, e.getMessage());
            return PaymentCancelResult.unknown("NICE_CANCEL_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
            log.error("NicePay cancel error — 취소 여부 미확정: tid={}, error={}",
                    pgTransactionId, e.getMessage());
            return PaymentCancelResult.unknown("NICE_CANCEL_ERROR", e.getMessage());
        }
    }

    /** 부분취소는 같은 orderId 로 재호출할 수 없다. tid 뒤에 시각을 붙여 매번 다르게 만든다 */
    private String cancelRequestId(String tid) {
        String suffix = "-" + System.currentTimeMillis();
        int room = 64 - suffix.length();
        return (tid.length() > room ? tid.substring(0, room) : tid) + suffix;
    }

    private String trim(String value, int maxBytes) {
        if (value == null) return "";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return value;
        // 멀티바이트 중간에서 자르면 깨진다 — 글자 단위로 줄인다
        String cut = value;
        while (cut.getBytes(StandardCharsets.UTF_8).length > maxBytes && !cut.isEmpty()) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    private HttpHeaders buildHeaders() {
        String encoded = Base64.getEncoder()
                .encodeToString((clientKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    private String str(Object value) { return value != null ? value.toString() : null; }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("응답 직렬화 실패", e);
            return "{}";
        }
    }

    private String extractJsonField(String json, String field) {
        if (json == null || json.isBlank()) return null;
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
