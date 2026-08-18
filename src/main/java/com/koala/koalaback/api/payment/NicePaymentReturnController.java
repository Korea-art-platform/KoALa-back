package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.security.NiceSignatureVerifier;
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
 * 나이스 결제창 인증 결과를 받는 곳.
 *
 * <h3>토스와 다른 점</h3>
 * <p>토스는 브라우저가 successUrl 로 돌아온 뒤 <b>프론트가</b> 승인 API 를 부른다.
 * 나이스는 인증이 끝나면 <b>여기로 POST</b> 가 오고, 이 요청을 받은 서버가 승인을 마친 뒤
 * 브라우저를 결과 화면으로 돌려보낸다.
 *
 * <h3>인증은 서명으로 한다</h3>
 * <p>이 POST 는 다른 도메인에서 오므로 세션 쿠키가 실리지 않는다. 로그인 여부를 알 수 없다.
 * 대신 서명이 "나이스가 보냈고 금액이 그대로다"를 증명한다.
 * <b>서명 검증 전에는 아무것도 하지 않는다.</b>
 *
 * <h3>여기서 예외를 밖으로 던지면 안 된다</h3>
 * <p>사용자는 지금 결제창에서 돌아오는 중이다. 500 을 그대로 보여주면 결제가 됐는지
 * 안 됐는지 모른 채 흰 화면을 만난다. 어떤 경우에도 결과 화면으로 리다이렉트한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nicepay.enabled", havingValue = "true")
public class NicePaymentReturnController {

    private static final String AUTH_SUCCESS = "0000";

    private final PaymentService paymentService;
    private final NiceSignatureVerifier signatureVerifier;

    @Value("${koala.web-base-url:https://koala-art.co.kr}")
    private String webBaseUrl;

    @PostMapping(value = "/api/v1/payments/nice/return")
    public ResponseEntity<Void> handleReturn(
            @RequestParam(required = false) String authResultCode,
            @RequestParam(required = false) String authResultMsg,
            @RequestParam(required = false) String tid,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String authToken,
            @RequestParam(required = false) String signature) {

        if (!AUTH_SUCCESS.equals(authResultCode)) {
            // 사용자가 취소했거나 카드사에서 거절된 경우 — 승인 단계까지 가지 않는다
            log.info("NicePay 인증 실패/취소: orderId={}, code={}, msg={}",
                    orderId, authResultCode, authResultMsg);
            return redirectToFail(orderId, authResultCode, authResultMsg);
        }

        if (!signatureVerifier.verify(authToken, amount, signature)) {
            // 서명이 안 맞으면 나이스가 보낸 요청이 아니거나 값이 바뀐 것이다
            log.error("★NicePay 서명 검증 실패★ 승인하지 않는다: orderId={}, tid={}", orderId, tid);
            return redirectToFail(orderId, "SIGNATURE_INVALID", "결제 정보 검증에 실패했습니다.");
        }

        try {
            paymentService.confirmVerifiedByPg(
                    new PaymentDto.ConfirmRequest(tid, orderId, new BigDecimal(amount)));

            log.info("NicePay 승인 완료: orderId={}, tid={}", orderId, tid);
            return redirect(webBaseUrl + "/payment/success?orderNo=" + encode(orderId));

        } catch (BusinessException e) {
            log.error("NicePay 승인 실패: orderId={}, tid={}, error={}",
                    orderId, tid, e.getMessage());
            return redirectToFail(orderId, e.getErrorCode().name(), e.getMessage());
        } catch (Exception e) {
            log.error("NicePay 승인 중 예외: orderId={}, tid={}", orderId, tid, e);
            return redirectToFail(orderId, "CONFIRM_ERROR", "결제 처리 중 오류가 발생했습니다.");
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
        // 303 이어야 브라우저가 POST 를 GET 으로 바꿔 다시 보낸다. 302 면 POST 로 재요청할 수 있다
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    private String encode(String value) {
        return value == null ? "" : java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
