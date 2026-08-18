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

@Slf4j
@Component
@ConditionalOnProperty(name = "payple.enabled", havingValue = "true")
public class PaypleAuthClient {
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

    public Map<String, Object> authenticateForCancel() {
        Map<String, Object> body = new HashMap<>();
        body.put("PCD_PAYCANCEL_FLAG", "Y");
        return requestAuth(body);
    }

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
