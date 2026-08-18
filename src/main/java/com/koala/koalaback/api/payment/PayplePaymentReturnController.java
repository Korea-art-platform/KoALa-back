package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 페이플 결제창 인증 결과를 받는 곳.
 *
 * <h3>나이스와 다른 점 — 서명이 없다</h3>
 * <p>나이스는 {@code sha256(authToken + clientId + amount + secretKey)} 서명을 함께 보내
 * "우리만 검증할 수 있는" 증거가 있다. 페이플은 그런 서명이 없다.
 *
 * <p>그래서 <b>이 POST 의 내용을 믿지 않는다.</b> 여기서 받은 값 중 신뢰하는 것은
 * 주문번호와 요청키뿐이고, <b>금액은 우리 DB 의 결제 요청 금액을 쓴다.</b>
 * 누군가 이 주소로 금액을 낮춰 POST 해도 승인 금액이 바뀌지 않는다.
 *
 * <p>실제 방어는 승인 API 가 한다 — 페이플은 인증 시점에 확정된 금액으로만 승인하고,
 * {@code PCD_PAY_REQKEY} 는 그 인증에서만 나온다. 우리가 만들 수 없는 값이다.
 * 그리고 {@code beginConfirm} 이 우리 DB 의 요청 금액과 대조한다.
 *
 * <h3>어떤 경우에도 결과 화면으로 보낸다</h3>
 * <p>사용자는 결제창에서 돌아오는 중이다. 500 을 그대로 보여주면 결제가 됐는지 모른 채
 * 흰 화면을 만난다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payple.enabled", havingValue = "true")
public class PayplePaymentReturnController {

    private static final String AUTH_SUCCESS = "success";

    private final PaymentService paymentService;

    @Value("${koala.web-base-url:https://koala-art.co.kr}")
    private String webBaseUrl;

    @PostMapping("/api/v1/payments/payple/return")
    public ResponseEntity<Void> handleReturn(
            @RequestParam(name = "PCD_PAY_RST", required = false) String payResult,
            @RequestParam(name = "PCD_PAY_CODE", required = false) String payCode,
            @RequestParam(name = "PCD_PAY_MSG", required = false) String payMessage,
            @RequestParam(name = "PCD_PAY_OID", required = false) String orderId,
            @RequestParam(name = "PCD_PAY_REQKEY", required = false) String payReqKey,
            @RequestParam(name = "PCD_PAY_TOTAL", required = false) String payTotal) {

        if (!AUTH_SUCCESS.equalsIgnoreCase(payResult)) {
            log.info("Payple 인증 실패/취소: orderId={}, code={}, msg={}", orderId, payCode, payMessage);
            return redirectToFail(orderId, payCode, payMessage);
        }

        if (orderId == null || orderId.isBlank() || payReqKey == null || payReqKey.isBlank()) {
            log.error("Payple 인증 결과에 주문번호/요청키가 없다: orderId={}", orderId);
            return redirectToFail(orderId, "MISSING_FIELDS", "결제 정보가 올바르지 않습니다.");
        }

        try {
            // 금액은 결제창이 보낸 값을 그대로 쓰지 않는다. beginConfirm 이 우리 DB 의
            // 요청 금액과 대조하므로, 여기서 넘긴 값이 다르면 승인 전에 걸린다.
            BigDecimal amount = parseAmount(payTotal);

            paymentService.confirmVerifiedByPg(
                    new PaymentDto.ConfirmRequest(payReqKey, orderId, amount));

            log.info("Payple 승인 완료: orderId={}", orderId);
            return redirect(webBaseUrl + "/payment/success?orderNo=" + encode(orderId));

        } catch (BusinessException e) {
            log.error("Payple 승인 실패: orderId={}, error={}", orderId, e.getMessage());
            return redirectToFail(orderId, e.getErrorCode().name(), e.getMessage());
        } catch (Exception e) {
            log.error("Payple 승인 중 예외: orderId={}", orderId, e);
            return redirectToFail(orderId, "CONFIRM_ERROR", "결제 처리 중 오류가 발생했습니다.");
        }
    }

    /** 금액이 비었거나 숫자가 아니면 0 을 넘겨 금액 대조에서 걸리게 한다 */
    private BigDecimal parseAmount(String value) {
        try {
            return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.error("Payple 금액 파싱 실패: {}", value);
            return BigDecimal.ZERO;
        }
    }

    private ResponseEntity<Void> redirectToFail(String orderId, String code, String message) {
        String url = UriComponentsBuilder.fromUriString(webBaseUrl + "/payment/fail")
                .queryParam("orderNo", orderId == null ? "" : orderId)
                .queryParam("code", code == null ? "" : code)
                .queryParam("message", message == null ? "" : message)
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return redirect(url);
    }

    private ResponseEntity<Void> redirect(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(url));
        // 303 이어야 브라우저가 POST 를 GET 으로 바꿔 다시 보낸다
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    private String encode(String value) {
        return value == null ? "" : java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
