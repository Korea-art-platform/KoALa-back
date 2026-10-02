package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.order.service.OnSitePaymentService;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.security.NiceSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("나이스 결제 리턴")
class NicePaymentReturnControllerTest {
    private static final String BASE = "https://koala-art.co.kr";

    private PaymentService paymentService;
    private NiceSignatureVerifier verifier;
    private OnSitePaymentService onSitePaymentService;
    private NicePaymentReturnController controller;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        verifier = mock(NiceSignatureVerifier.class);
        onSitePaymentService = mock(OnSitePaymentService.class);
        when(onSitePaymentService.payTokenOf(any())).thenReturn(Optional.empty());
        controller = new NicePaymentReturnController(paymentService, verifier, onSitePaymentService);
        ReflectionTestUtils.setField(controller, "webBaseUrl", BASE);
    }

    private static MockHttpServletRequest request(String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/api/v1/payments/nice/return");
        req.addHeader("User-Agent", "Mozilla/5.0 (iPhone) Safari");
        return req;
    }

    private static String location(ResponseEntity<Void> res) {
        return URLDecoder.decode(res.getHeaders().getLocation().toString(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("모바일에서 결과 값 없이 GET 으로 열리면 404 대신 실패 화면으로 보내고 승인하지 않는다")
    void getWithoutResultGoesToFailPage() {
        ResponseEntity<Void> res = controller.handleReturn(request("GET"),
                null, null, null, null, null, null, null);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(location(res)).startsWith(BASE + "/payment/fail").contains("RETURN_WITHOUT_RESULT");
        verify(paymentService, never()).confirmVerifiedByPg(any());
    }

    @Test
    @DisplayName("GET 이어도 인증 결과와 서명이 맞으면 승인하고 완료 화면으로 보낸다")
    void getWithValidResultIsConfirmed() {
        when(verifier.verify("auth", "1000", "sig")).thenReturn(true);

        ResponseEntity<Void> res = controller.handleReturn(request("GET"),
                "0000", "성공", "tid-1", "KL-1", "1000", "auth", "sig");

        verify(paymentService).confirmVerifiedByPg(any());
        assertThat(location(res)).isEqualTo(BASE + "/payment/success?orderNo=KL-1");
    }

    @Test
    @DisplayName("현장결제 주문은 승인 후 그 결제 페이지로 돌려보낸다")
    void onSiteSuccessReturnsToPayPage() {
        when(verifier.verify("auth", "1000", "sig")).thenReturn(true);
        when(onSitePaymentService.payTokenOf("KL-2")).thenReturn(Optional.of("tok"));

        ResponseEntity<Void> res = controller.handleReturn(request("POST"),
                "0000", "성공", "tid-2", "KL-2", "1000", "auth", "sig");

        assertThat(location(res)).isEqualTo(BASE + "/pay/tok");
    }

    @Test
    @DisplayName("현장결제 주문이 실패하면 결제 페이지로 돌아가 사유를 보여 준다")
    void onSiteFailureReturnsToPayPageWithReason() {
        when(onSitePaymentService.payTokenOf("KL-3")).thenReturn(Optional.of("tok3"));

        ResponseEntity<Void> res = controller.handleReturn(request("POST"),
                "9999", "사용자 취소", null, "KL-3", "1000", null, null);

        assertThat(location(res)).isEqualTo(BASE + "/pay/tok3?failed=사용자 취소");
        verify(paymentService, never()).confirmVerifiedByPg(any());
    }

    @Test
    @DisplayName("서명이 맞지 않으면 승인하지 않는다")
    void invalidSignatureIsRejected() {
        when(verifier.verify(any(), any(), any())).thenReturn(false);

        ResponseEntity<Void> res = controller.handleReturn(request("POST"),
                "0000", "성공", "tid-4", "KL-4", "1000", "auth", "forged");

        assertThat(location(res)).contains("SIGNATURE_INVALID");
        verify(paymentService, never()).confirmVerifiedByPg(any());
    }
}
