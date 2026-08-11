package com.koala.koalaback.domain.payment.service;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.provider.PaymentProvider;
import com.koala.koalaback.domain.payment.repository.PaymentEventRepository;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * 결제 승인 정책 검증 — 특히 "PG 응답을 못 받은" 미확정 케이스의 처리.
 *
 * <p>가장 위험한 시나리오는 승인은 됐는데 응답을 못 받은 경우다.
 * 이때 실패로 단정하면 돈은 빠져나갔는데 주문은 미결제로 남는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("결제 승인 정책")
class PaymentConfirmPolicyTest {

    private static final Long PAYMENT_ID = 100L;
    private static final String ORDER_NO = "ORD-1";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(50_000);

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentEventRepository paymentEventRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CodeGenerator codeGenerator;
    @Mock private ObjectMapper objectMapper;
    @Mock private PaymentTransactionService paymentTransactionService;
    @Mock private PaymentProvider provider;

    private PaymentService paymentService;
    private PaymentDto.ConfirmRequest request;

    @BeforeEach
    void setUp() {
        given(provider.getProviderCode()).willReturn("TOSS");

        paymentService = new PaymentService(
                paymentRepository, paymentEventRepository, orderRepository,
                codeGenerator, List.of(provider), objectMapper, paymentTransactionService);

        request = mock(PaymentDto.ConfirmRequest.class);

        given(paymentTransactionService.beginConfirm(eq(1L), any()))
                .willReturn(new PaymentTransactionService.ConfirmContext(
                        PAYMENT_ID, "TOSS", ORDER_NO, AMOUNT));
    }

    @Test
    @DisplayName("승인 성공 — PG 호출은 사전 검증 트랜잭션이 끝난 뒤에 일어난다")
    void confirm_approved_callsPgAfterPreCheckCommits() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.approved(
                        "pk_1", "A1", AMOUNT, "{}"));

        paymentService.confirm(1L, request);

        // 순서가 핵심 — 선점 커밋 → PG 호출 → 결과 반영
        InOrder order = inOrder(paymentTransactionService, provider);
        order.verify(paymentTransactionService).beginConfirm(eq(1L), any());
        order.verify(provider).confirm(any(), eq(ORDER_NO), eq(AMOUNT));
        order.verify(paymentTransactionService).applyConfirmApproved(eq(PAYMENT_ID), any());

        then(paymentTransactionService).should(never())
                .applyConfirmInDoubt(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("PG 가 명시적으로 거절하면 실패로 확정한다")
    void confirm_rejected_marksFailed() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.rejected("REJECT", "한도 초과"));

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR));

        then(paymentTransactionService).should()
                .applyConfirmRejected(PAYMENT_ID, "REJECT", "한도 초과");
        then(provider).should(never()).lookup(anyString());
    }

    @Test
    @DisplayName("타임아웃이어도 재조회에서 승인 확인되면 정상 승인 처리한다 — 가장 위험한 케이스")
    void confirm_timeoutButActuallyApproved_recoversAsApproved() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.unknown("TOSS_NO_RESPONSE", "read timed out"));
        given(provider.lookup(ORDER_NO))
                .willReturn(new PaymentProvider.PaymentLookupResult(
                        true, true, true, "pk_1", "A1", AMOUNT, "{}"));

        paymentService.confirm(1L, request);

        // 실패로 단정하지 않고 승인으로 확정해야 한다
        then(paymentTransactionService).should().applyConfirmApproved(eq(PAYMENT_ID), any());
        then(paymentTransactionService).should(never())
                .applyConfirmRejected(any(), anyString(), anyString());
        then(paymentTransactionService).should(never())
                .applyConfirmInDoubt(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("타임아웃 후 재조회에서 미승인이 확인되면 그때만 실패로 확정한다")
    void confirm_timeoutAndConfirmedNotApproved_marksFailed() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.unknown("TOSS_NO_RESPONSE", "read timed out"));
        given(provider.lookup(ORDER_NO))
                .willReturn(new PaymentProvider.PaymentLookupResult(
                        true, false, false, null, null, null, null));

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(BusinessException.class);

        then(paymentTransactionService).should()
                .applyConfirmRejected(eq(PAYMENT_ID), anyString(), anyString());
        then(paymentTransactionService).should(never())
                .applyConfirmInDoubt(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("재조회조차 실패하면 IN_DOUBT 로 남긴다 — 실패로 단정하지 않는다")
    void confirm_timeoutAndLookupUnavailable_marksInDoubt() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.unknown("TOSS_NO_RESPONSE", "read timed out"));
        given(provider.lookup(ORDER_NO))
                .willReturn(PaymentProvider.PaymentLookupResult.unavailable());

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));

        then(paymentTransactionService).should()
                .applyConfirmInDoubt(eq(PAYMENT_ID), anyString(), anyString());
        // 실패로 확정하면 만료 취소가 돌아 "결제됐는데 주문 없음" 이 된다
        then(paymentTransactionService).should(never())
                .applyConfirmRejected(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("승인 저장이 실패하면 PG 승인을 되돌린다 — 돈만 빠져나간 상태를 막는다")
    void confirm_saveFails_compensatesByCancel() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.approved(
                        "pk_1", "A1", AMOUNT, "{}"));
        // DB 저장 실패
        given(paymentTransactionService.applyConfirmApproved(any(), any()))
                .willThrow(new RuntimeException("DB 연결 끊김"));
        given(provider.cancel(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentCancelResult.cancelled(AMOUNT, "{}"));

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));

        // 거래번호는 DB 가 아니라 PG 응답에서 가져와야 한다 (저장이 실패했으므로)
        then(provider).should().cancel(eq("pk_1"), eq(AMOUNT), anyString());
        // 보상에 성공했으니 실패로 확정 — 그래야 고객이 새 결제를 시작할 수 있다
        then(paymentTransactionService).should()
                .applyConfirmRejected(eq(PAYMENT_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("보상 취소까지 실패하면 IN_DOUBT 로 잠가 재시도를 막는다")
    void confirm_compensationFails_marksInDoubt() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.approved(
                        "pk_1", "A1", AMOUNT, "{}"));
        given(paymentTransactionService.applyConfirmApproved(any(), any()))
                .willThrow(new RuntimeException("DB 연결 끊김"));
        given(provider.cancel(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentCancelResult.rejected("ALREADY_CANCELED", "이미 취소됨"));

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));

        // 실패로 확정하면 고객이 재시도해 이중 결제가 된다 — IN_DOUBT 로 잠근다
        then(paymentTransactionService).should()
                .applyConfirmInDoubt(eq(PAYMENT_ID), anyString(), anyString());
        then(paymentTransactionService).should(never())
                .applyConfirmRejected(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("거래번호가 없으면 취소를 호출하지 않고 바로 IN_DOUBT 로 둔다")
    void confirm_saveFailsWithoutTxId_skipsCancel() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.approved(
                        null, "A1", AMOUNT, "{}"));
        given(paymentTransactionService.applyConfirmApproved(any(), any()))
                .willThrow(new RuntimeException("DB 연결 끊김"));

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));

        then(provider).should(never()).cancel(any(), any(), any());
        then(paymentTransactionService).should()
                .applyConfirmInDoubt(eq(PAYMENT_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("보상 결과를 DB 에 못 남겨도 원래 오류(IN_DOUBT)를 그대로 전달한다")
    void confirm_compensationRecordFails_stillReportsInDoubt() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.approved(
                        "pk_1", "A1", AMOUNT, "{}"));
        given(paymentTransactionService.applyConfirmApproved(any(), any()))
                .willThrow(new RuntimeException("DB 연결 끊김"));
        given(provider.cancel(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentCancelResult.cancelled(AMOUNT, "{}"));
        // 보상 결과 기록마저 실패 — DB 가 고장난 상황이므로 충분히 가능하다
        org.mockito.BDDMockito.willThrow(new RuntimeException("DB 여전히 죽어 있음"))
                .given(paymentTransactionService)
                .applyConfirmRejected(any(), anyString(), anyString());

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .as("기록 실패가 원래 오류를 가리면 안 된다")
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));
    }

    @Test
    @DisplayName("재조회로 승인을 확인한 경우에도 저장 실패 시 보상 취소한다")
    void confirm_recoveredByLookup_alsoCompensatesOnSaveFailure() {
        given(provider.confirm(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentConfirmResult.unknown("TOSS_NO_RESPONSE", "read timed out"));
        given(provider.lookup(ORDER_NO))
                .willReturn(new PaymentProvider.PaymentLookupResult(
                        true, true, true, "pk_1", "A1", AMOUNT, "{}"));
        given(paymentTransactionService.applyConfirmApproved(any(), any()))
                .willThrow(new RuntimeException("DB 연결 끊김"));
        given(provider.cancel(any(), any(), any()))
                .willReturn(PaymentProvider.PaymentCancelResult.cancelled(AMOUNT, "{}"));

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));

        then(provider).should().cancel(eq("pk_1"), eq(AMOUNT), anyString());
    }

    @Test
    @DisplayName("provider 가 예외를 던져도 실패로 단정하지 않고 재조회로 확인한다")
    void confirm_providerThrows_treatedAsUnknown() {
        given(provider.confirm(any(), any(), any()))
                .willThrow(new RuntimeException("connection reset"));
        given(provider.lookup(ORDER_NO))
                .willReturn(PaymentProvider.PaymentLookupResult.unavailable());

        assertThatThrownBy(() -> paymentService.confirm(1L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_IN_DOUBT));

        then(paymentTransactionService).should()
                .applyConfirmInDoubt(eq(PAYMENT_ID), anyString(), anyString());
    }
}
