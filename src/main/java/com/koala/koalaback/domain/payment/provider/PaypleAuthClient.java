package com.koala.koalaback.domain.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 페이플 파트너 인증.
 *
 * <h3>왜 별도 클래스인가</h3>
 * <p>토스·나이스는 시크릿 키를 헤더에 실어 바로 API 를 부른다. 페이플은
 * <b>모든 호출 앞에 인증을 한 번 더 거쳐</b> 일회성 AuthKey 를 받고, 그 키로 본 요청을 보낸다.
 * 승인·취소·조회마다 용도가 다른 플래그를 넣어야 해서 호출부에서 섞이면 읽기 어려워진다.
 *
 * <h3>30분 재사용</h3>
 * <p>인증은 30분간 유효하다. 매 요청마다 새로 받으면 왕복이 두 배가 되고
 * 페이플 쪽 호출 수도 두 배가 된다. 만료 여유를 두고 캐시한다.
 *
 * <p>다만 <b>취소용 인증은 캐시하지 않는다.</b> 환불 키가 함께 들어가는 별개 흐름이라
 * 승인용 토큰과 섞이면 엉뚱한 요청이 나갈 수 있다. 돈이 나가는 쪽은 매번 새로 받는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payple.enabled", havingValue = "true")
public class PaypleAuthClient {

    /** 30분 유효하지만 5분 앞서 만료로 본다 — 경계에서 실패하지 않게 */
    private static final long CACHE_TTL_SECONDS = 25 * 60;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String apiBase;
    private final String cstId;
    private final String custKey;
    private final String refundKey;
    private final String referer;

    private volatile Map<String, Object> cachedAuth;
    private volatile Instant cachedAt = Instant.EPOCH;

    public PaypleAuthClient(RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            @Value("${payple.api-base:https://democpay.payple.kr}") String apiBase,
                            @Value("${payple.cst-id:}") String cstId,
                            @Value("${payple.cust-key:}") String custKey,
                            @Value("${payple.refund-key:}") String refundKey,
                            @Value("${koala.web-base-url:https://koala-art.co.kr}") String referer) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.cstId = cstId;
        this.custKey = custKey;
        this.refundKey = refundKey;
        this.referer = referer;
    }

    public boolean isConfigured() {
        return !cstId.isBlank() && !custKey.isBlank();
    }

    public String getCstId() { return cstId; }
    public String getCustKey() { return custKey; }
    public String getRefundKey() { return refundKey; }
    public String getApiBase() { return apiBase; }

    /** 승인·조회용 인증 — 30분 캐시 */
    public Map<String, Object> authenticate() {
        Map<String, Object> cached = cachedAuth;
        if (cached != null && cachedAt.plusSeconds(CACHE_TTL_SECONDS).isAfter(Instant.now())) {
            return cached;
        }
        Map<String, Object> fresh = requestAuth(new HashMap<>());
        if (fresh != null) {
            cachedAuth = fresh;
            cachedAt = Instant.now();
        }
        return fresh;
    }

    /**
     * 취소용 인증 — 캐시하지 않는다.
     *
     * <p>환불 키가 들어가는 별개 흐름이라 승인용과 섞이면 안 된다.
     * 환불은 자주 일어나지 않으므로 매번 받아도 부담이 없다.
     */
    public Map<String, Object> authenticateForCancel() {
        Map<String, Object> body = new HashMap<>();
        body.put("PCD_PAYCANCEL_FLAG", "Y");
        return requestAuth(body);
    }

    /**
     * 조회용 인증.
     *
     * <p>승인용과 플래그가 다르다. 캐시하지 않는 이유는 취소와 같다 —
     * 용도가 다른 토큰이 섞이면 엉뚱한 요청이 나간다.
     */
    public Map<String, Object> authenticateForCheck() {
        Map<String, Object> body = new HashMap<>();
        body.put("PCD_PAYCHK_FLAG", "Y");
        return requestAuth(body);
    }

    private Map<String, Object> requestAuth(Map<String, Object> extra) {
        try {
            Map<String, Object> body = new HashMap<>(extra);
            body.put("cst_id", cstId);
            body.put("custKey", custKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 페이플은 등록된 도메인에서 오는 요청만 받는다
            headers.set(HttpHeaders.REFERER, referer);

            ResponseEntity<Map> response = restTemplate.exchange(
                    apiBase + "/php/auth.php",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null || !"success".equals(String.valueOf(res.get("result")))) {
                log.error("Payple 파트너 인증 실패: {}", res != null ? res.get("result_msg") : "본문 없음");
                return null;
            }
            return res;

        } catch (Exception e) {
            log.error("Payple 파트너 인증 중 오류: {}", e.getMessage());
            return null;
        }
    }

    /** 인증 응답이 알려준 실제 호출 주소. 페이플이 호스트를 바꿔도 따라간다 */
    public String resolveUrl(Map<String, Object> auth, String fallbackPath) {
        Object host = auth.get("PCD_PAY_HOST");
        Object path = auth.get("return_url");
        if (host != null && path != null) {
            return host.toString() + path.toString();
        }
        return apiBase + fallbackPath;
    }

    public HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.REFERER, referer);
        return headers;
    }
}
