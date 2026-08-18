package com.koala.koalaback.domain.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 페이플 결제 연동.
 *
 * <h3>세 PG 중 가장 다르다</h3>
 * <p>토스·나이스는 시크릿을 헤더에 실어 바로 부른다. 페이플은 <b>호출마다 파트너 인증을
 * 먼저 거쳐</b> 일회성 AuthKey 를 받고, 그 키로 본 요청을 보낸다.
 * 그래서 실패 지점이 하나 더 있다 — 인증 실패와 승인 실패를 구분해야 한다.
 *
 * <h3>인증 실패는 UNKNOWN 이 아니다</h3>
 * <p>파트너 인증이 실패하면 승인 요청 자체가 나가지 않았다. 즉 결제는 확실히 안 됐다.
 * 그런데도 {@code UNKNOWN} 으로 두는 이유는, 이 시점에 이미 <b>결제창 인증은 끝난</b>
 * 상태이기 때문이다. 고객 카드에 승인이 잡혔을 수 있어 "확실히 실패"라고 단정할 수 없다.
 * 모르는 것은 모른다고 둔다.
 *
 * <h3>승인은 10분 안에</h3>
 * <p>결제창 인증 후 10분이 지나면 페이플이 거절한다(PCCFD001). 정상 흐름에서는 수초라
 * 문제되지 않지만, 우리 서버가 느려 밀리면 여기서 걸린다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payple.enabled", havingValue = "true")
public class PayplePaymentProvider implements PaymentProvider {

    private static final String RESULT_SUCCESS = "success";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaypleAuthClient authClient;
    private final String confirmPath;

    public PayplePaymentProvider(RestTemplate restTemplate,
                                 ObjectMapper objectMapper,
                                 PaypleAuthClient authClient,
                                 @Value("${payple.confirm-path:/api/v1/payments/cards/approval/confirm}")
                                 String confirmPath) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.authClient = authClient;
        this.confirmPath = confirmPath;
    }

    @Override
    public String getProviderCode() { return "PAYPLE"; }

    /**
     * 승인.
     *
     * <p>{@code paymentKey} 는 결제창 인증 결과로 받은 {@code PCD_PAY_REQKEY} 다.
     * 페이플은 승인 요청에 금액을 받지 않는다 — 인증 시점 금액으로 승인된다.
     * 그래서 <b>금액 대조는 우리 쪽에서 이미 끝나 있어야 한다</b>
     * (결제창 복귀 시 서명·금액 검증, beginConfirm 의 requestedAmount 비교).
     */
    @Override
    public PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        try {
            Map<String, Object> auth = authClient.authenticate();
            if (auth == null) {
                log.error("Payple 승인 전 파트너 인증 실패 — 승인 여부 미확정: orderId={}", orderId);
                return PaymentConfirmResult.unknown("PAYPLE_AUTH_FAILED", "파트너 인증에 실패했습니다.");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("PCD_CST_ID", auth.get("cst_id"));
            body.put("PCD_CUST_KEY", auth.get("custKey"));
            body.put("PCD_AUTH_KEY", auth.get("AuthKey"));
            body.put("PCD_PAY_REQKEY", paymentKey);

            ResponseEntity<Map> response = restTemplate.exchange(
                    authClient.resolveUrl(auth, confirmPath),
                    HttpMethod.POST,
                    new HttpEntity<>(body, authClient.jsonHeaders()),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null) {
                return PaymentConfirmResult.unknown("PAYPLE_EMPTY_BODY", "응답 본문이 비어 있습니다.");
            }

            String rst = str(res.get("PCD_PAY_RST"));
            if (!RESULT_SUCCESS.equals(rst)) {
                log.error("Payple confirm rejected: orderId={}, code={}, msg={}",
                        orderId, res.get("PCD_PAY_CODE"), res.get("PCD_PAY_MSG"));
                return PaymentConfirmResult.rejected(str(res.get("PCD_PAY_CODE")), str(res.get("PCD_PAY_MSG")));
            }

            BigDecimal approved = res.get("PCD_PAY_TOTAL") != null
                    ? new BigDecimal(str(res.get("PCD_PAY_TOTAL"))) : amount;

            // 페이플은 승인 요청에 금액을 안 받으므로, 돌아온 금액이 다르면 여기서 잡는다
            if (approved.compareTo(amount) != 0) {
                log.error("★Payple 승인 금액 불일치★ orderId={}, 요청={}, 승인={}", orderId, amount, approved);
            }

            // 취소는 주문번호 + 결제일자를 요구한다. 일자를 여기서 붙여 두지 않으면
            // 나중에 취소할 때 날짜를 추측해야 하고, 추측이 틀리면 "환불이 됐는지 모르는"
            // 상태가 된다. 승인 시점에 확정된 값을 그대로 실어 보낸다.
            return PaymentConfirmResult.approved(
                    packTransactionId(str(res.get("PCD_PAY_OID")), str(res.get("PCD_PAY_TIME"))),
                    str(res.get("PCD_PAY_CARDAUTHNO")),
                    approved,
                    toJson(res));

        } catch (HttpServerErrorException e) {
            log.error("Payple confirm 5xx — 승인 여부 미확정: orderId={}, status={}", orderId, e.getStatusCode());
            return PaymentConfirmResult.unknown("PAYPLE_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Payple confirm 응답 없음 — 승인 여부 미확정: orderId={}", orderId);
            return PaymentConfirmResult.unknown("PAYPLE_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
            log.error("Payple confirm error — 승인 여부 미확정: orderId={}, error={}", orderId, e.getMessage());
            return PaymentConfirmResult.unknown("PAYPLE_ERROR", e.getMessage());
        }
    }

    /**
     * 주문번호로 재조회 — 미확정을 푸는 경로.
     *
     * <p>페이플도 결제일자를 함께 요구한다. 나이스와 같은 이유로 오늘(KST) 먼저,
     * 없으면 어제로 한 번 더 본다.
     *
     * <p>조회는 초당 2회 제한이 있다. 미확정 복구는 드물게 일어나므로 문제되지 않지만,
     * 이 메서드를 반복 호출하는 코드를 만들면 안 된다.
     */
    @Override
    public PaymentLookupResult lookup(String orderId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        PaymentLookupResult result = lookupOn(orderId, today);
        if (result.queried() && !result.found()) {
            PaymentLookupResult yesterday = lookupOn(orderId, today.minusDays(1));
            if (yesterday.found()) return yesterday;
        }
        return result;
    }

    private PaymentLookupResult lookupOn(String orderId, LocalDate payDate) {
        String date = payDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        try {
            Map<String, Object> auth = authClient.authenticateForCheck();
            if (auth == null) return PaymentLookupResult.unavailable();

            Map<String, Object> body = new HashMap<>();
            body.put("PCD_CST_ID", auth.get("cst_id"));
            body.put("PCD_CUST_KEY", auth.get("custKey"));
            body.put("PCD_AUTH_KEY", auth.get("AuthKey"));
            body.put("PCD_PAYCHK_FLAG", "Y");
            body.put("PCD_PAY_OID", orderId);
            body.put("PCD_PAY_DATE", date);

            ResponseEntity<Map> response = restTemplate.exchange(
                    authClient.resolveUrl(auth, "/php/PayChkAct.php"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, authClient.jsonHeaders()),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null) return PaymentLookupResult.unavailable();

            if (!RESULT_SUCCESS.equals(str(res.get("PCD_PAY_RST")))) {
                log.info("Payple lookup: orderId={}, date={} 거래 없음", orderId, date);
                return new PaymentLookupResult(true, false, false, null, null, null, null);
            }

            // 조회는 성공했고 거래도 있다. 승인 상태인지는 PCD_PAY_STATE 로 본다
            boolean approved = "1".equals(str(res.get("PCD_PAY_STATE")));
            log.info("Payple lookup: orderId={}, date={}, state={}", orderId, date, res.get("PCD_PAY_STATE"));

            return new PaymentLookupResult(
                    true, true, approved,
                    str(res.get("PCD_PAY_OID")),
                    str(res.get("PCD_PAY_CARDAUTHNO")),
                    res.get("PCD_PAY_TOTAL") != null ? new BigDecimal(str(res.get("PCD_PAY_TOTAL"))) : null,
                    toJson(res));

        } catch (Exception e) {
            log.error("Payple lookup 실패: orderId={}, date={}, error={}", orderId, date, e.getMessage());
            return PaymentLookupResult.unavailable();
        }
    }

    /**
     * 취소(환불).
     *
     * <p>페이플은 tid 가 아니라 <b>주문번호 + 결제일자</b>로 취소한다. 그래서
     * {@code pgTransactionId} 에 주문번호가 들어온다 — 승인 시 PCD_PAY_OID 를
     * pgTransactionId 로 저장했기 때문이다.
     *
     * <p>결제일자를 모르면 취소할 수 없어 조회로 먼저 찾는다. 조회가 안 되면
     * 취소를 시도하지 않고 UNKNOWN 으로 둔다 — 엉뚱한 날짜로 부르면 실패가 아니라
     * "취소됐는지 모르는" 상태가 된다.
     */
    @Override
    public PaymentCancelResult cancel(String pgTransactionId, BigDecimal cancelAmount, String reason) {
        try {
            String[] parts = unpackTransactionId(pgTransactionId);
            String orderId = parts[0];
            String payDate = parts[1];
            if (payDate == null) {
                log.error("Payple cancel — 결제일자가 없어 취소를 시도하지 않는다: oid={}", orderId);
                return PaymentCancelResult.unknown("PAYPLE_PAYDATE_MISSING",
                        "원거래 결제일자를 확인하지 못했습니다.");
            }

            Map<String, Object> auth = authClient.authenticateForCancel();
            if (auth == null) {
                return PaymentCancelResult.unknown("PAYPLE_AUTH_FAILED", "파트너 인증에 실패했습니다.");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("PCD_CST_ID", auth.get("cst_id"));
            body.put("PCD_CUST_KEY", auth.get("custKey"));
            body.put("PCD_AUTH_KEY", auth.get("AuthKey"));
            body.put("PCD_REFUND_KEY", authClient.getRefundKey());
            body.put("PCD_PAYCANCEL_FLAG", "Y");
            body.put("PCD_PAY_OID", orderId);
            body.put("PCD_PAY_DATE", payDate);
            body.put("PCD_REFUND_TOTAL", cancelAmount.longValue());

            ResponseEntity<Map> response = restTemplate.exchange(
                    authClient.resolveUrl(auth, "/php/account/api/cPayCAct.php"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, authClient.jsonHeaders()),
                    Map.class);

            Map<String, Object> res = response.getBody();
            if (res == null) {
                return PaymentCancelResult.unknown("PAYPLE_CANCEL_EMPTY_BODY", "응답 본문이 비어 있습니다.");
            }

            if (!RESULT_SUCCESS.equals(str(res.get("PCD_PAY_RST")))) {
                log.error("Payple cancel rejected: oid={}, code={}, msg={}",
                        pgTransactionId, res.get("PCD_PAY_CODE"), res.get("PCD_PAY_MSG"));
                return PaymentCancelResult.rejected(str(res.get("PCD_PAY_CODE")), str(res.get("PCD_PAY_MSG")));
            }
            return PaymentCancelResult.cancelled(cancelAmount, toJson(res));

        } catch (HttpServerErrorException e) {
            log.error("Payple cancel 5xx — 취소 여부 미확정: oid={}", pgTransactionId);
            return PaymentCancelResult.unknown("PAYPLE_CANCEL_SERVER_ERROR", e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Payple cancel 응답 없음 — 취소 여부 미확정: oid={}", pgTransactionId);
            return PaymentCancelResult.unknown("PAYPLE_CANCEL_NO_RESPONSE", e.getMessage());
        } catch (Exception e) {
            log.error("Payple cancel error — 취소 여부 미확정: oid={}, error={}", pgTransactionId, e.getMessage());
            return PaymentCancelResult.unknown("PAYPLE_CANCEL_ERROR", e.getMessage());
        }
    }

    /**
     * 주문번호와 결제일자를 하나의 거래키로 묶는다.
     *
     * <p>다른 PG 는 tid 하나로 취소가 되지만 페이플은 일자가 더 필요하다.
     * 인터페이스를 바꾸는 대신 저장 값에 실어 보낸다 — 이 클래스가 넣고 이 클래스가 꺼내므로
     * 바깥은 여전히 "거래키 문자열" 하나만 안다.
     */
    static String packTransactionId(String orderId, String payTime) {
        if (orderId == null) return null;
        if (payTime == null || payTime.length() < 8) return orderId;
        return orderId + "|" + payTime.substring(0, 8);
    }

    /** @return [주문번호, 결제일자] — 일자가 없으면 두 번째가 null */
    static String[] unpackTransactionId(String packed) {
        if (packed == null) return new String[]{null, null};
        int sep = packed.lastIndexOf('|');
        if (sep < 0) return new String[]{packed, null};
        return new String[]{packed.substring(0, sep), packed.substring(sep + 1)};
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
}
