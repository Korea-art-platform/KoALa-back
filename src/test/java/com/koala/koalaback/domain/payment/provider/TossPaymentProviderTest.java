package com.koala.koalaback.domain.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Toss 결제 Provider")
class TossPaymentProviderTest {
    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String LOOKUP_URL = "https://api.tosspayments.com/v1/payments/orders/ORD-1";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private TossPaymentProvider provider;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        provider = new TossPaymentProvider(restTemplate, new ObjectMapper(), new MockEnvironment());
        ReflectionTestUtils.setField(provider, "secretKey", "test_sk_dummy");
    }

    @Test
    @DisplayName("승인 성공 — 응답에서 거래 정보를 파싱한다")
    void confirm_success_parsesApproval() {
        mockServer.expect(requestTo(CONFIRM_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk_123","approvalNo":"A999","totalAmount":53000,"status":"DONE"}
                        """, MediaType.APPLICATION_JSON));

        PaymentProvider.PaymentConfirmResult result =
                provider.confirm("pk_123", "ORD-1", BigDecimal.valueOf(53_000));

        assertThat(result.isApproved()).isTrue();
        assertThat(result.pgTransactionId()).isEqualTo("pk_123");
        assertThat(result.approvedAmount()).isEqualByComparingTo(BigDecimal.valueOf(53_000));
        mockServer.verify();
    }

    @Test
    @DisplayName("4xx 거절 — REJECTED 로 확정한다(재조회 불필요)")
    void confirm_clientError_isRejected() {
        mockServer.expect(requestTo(CONFIRM_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                              {"code":"EXCEED_MAX_CARD_INSTALLMENT_PLAN","message":"할부 개월 수가 초과되었습니다."}
                              """));

        PaymentProvider.PaymentConfirmResult result =
                provider.confirm("pk_123", "ORD-1", BigDecimal.valueOf(53_000));

        assertThat(result.outcome()).isEqualTo(PaymentProvider.Outcome.REJECTED);
        assertThat(result.isUnknown()).as("거절은 미확정이 아니다").isFalse();
        assertThat(result.failureCode()).isEqualTo("EXCEED_MAX_CARD_INSTALLMENT_PLAN");
    }

    @Test
    @DisplayName("5xx — 승인됐을 수 있으므로 UNKNOWN 으로 둔다")
    void confirm_serverError_isUnknown() {
        mockServer.expect(requestTo(CONFIRM_URL)).andRespond(withServerError());

        PaymentProvider.PaymentConfirmResult result =
                provider.confirm("pk_123", "ORD-1", BigDecimal.valueOf(53_000));

        assertThat(result.isUnknown()).isTrue();
        assertThat(result.isApproved()).isFalse();
    }

    @Test
    @DisplayName("타임아웃 — 실패로 단정하지 않고 UNKNOWN 으로 둔다 (가장 위험한 케이스)")
    void confirm_timeout_isUnknown() {
        mockServer.expect(requestTo(CONFIRM_URL))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out");
                });

        PaymentProvider.PaymentConfirmResult result =
                provider.confirm("pk_123", "ORD-1", BigDecimal.valueOf(53_000));

        assertThat(result.outcome()).isEqualTo(PaymentProvider.Outcome.UNKNOWN);
        assertThat(result.failureCode()).isEqualTo("TOSS_NO_RESPONSE");
    }

    @Test
    @DisplayName("재조회 — DONE 이면 승인 확인으로 판정한다")
    void lookup_done_isApproved() {
        mockServer.expect(requestTo(LOOKUP_URL))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk_123","approvalNo":"A999","totalAmount":53000,"status":"DONE"}
                        """, MediaType.APPLICATION_JSON));

        PaymentProvider.PaymentLookupResult result = provider.lookup("ORD-1");

        assertThat(result.queried()).isTrue();
        assertThat(result.approved()).isTrue();
        assertThat(result.pgTransactionId()).isEqualTo("pk_123");
    }

    @Test
    @DisplayName("재조회 — 404 면 미승인 확정으로 판정한다")
    void lookup_notFound_isDefinitelyNotApproved() {
        mockServer.expect(requestTo(LOOKUP_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        PaymentProvider.PaymentLookupResult result = provider.lookup("ORD-1");

        assertThat(result.isDefinitelyNotApproved())
                .as("PG 에 결제가 없으면 실패로 확정해도 안전하다").isTrue();
    }

    @Test
    @DisplayName("재조회 자체가 실패하면 여전히 알 수 없음으로 남긴다")
    void lookup_error_isUnavailable() {
        mockServer.expect(requestTo(LOOKUP_URL)).andRespond(withServerError());

        PaymentProvider.PaymentLookupResult result = provider.lookup("ORD-1");

        assertThat(result.queried()).isFalse();
        assertThat(result.isDefinitelyNotApproved())
                .as("모르는 상태를 미승인으로 단정하면 안 된다").isFalse();
    }
}
