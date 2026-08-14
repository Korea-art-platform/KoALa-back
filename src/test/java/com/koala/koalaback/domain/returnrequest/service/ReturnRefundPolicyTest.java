package com.koala.koalaback.domain.returnrequest.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.payment.entity.Payment;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import com.koala.koalaback.domain.returnrequest.repository.ReturnRequestRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("반품 환불 정책")
class ReturnRefundPolicyTest {
    private static final String RETURN_NO = "RET-001";
    private static final BigDecimal ORDER_TOTAL = new BigDecimal("100000");

    @InjectMocks private ReturnRequestTransactionService transactionService;

    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StockService stockService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = mock(Order.class);
        given(order.getId()).willReturn(1L);
        given(order.getTotalAmount()).willReturn(ORDER_TOTAL);
        given(order.getOrderItems()).willReturn(List.of());

        Payment captured = mock(Payment.class);
        given(captured.getStatus()).willReturn("CAPTURED");
        given(captured.getPaymentNo()).willReturn("PAY-001");
        given(paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(anyLong()))
                .willReturn(Optional.of(captured));
    }

    private ReturnRequest pendingReturn(String returnType) {
        ReturnRequest request = ReturnRequest.builder()
                .returnNo(RETURN_NO)
                .order(order)
                .returnType(returnType)
                .reason("단순 변심")
                .build();
        given(returnRequestRepository.findByReturnNo(RETURN_NO)).willReturn(Optional.of(request));
        return request;
    }

    private OrderItem orderItem(Long skuId, int quantity, Long itemId) {
        Sku sku = mock(Sku.class);
        given(sku.getId()).willReturn(skuId);

        OrderItem item = mock(OrderItem.class);
        given(item.getSku()).willReturn(sku);
        given(item.getQuantity()).willReturn(quantity);
        given(item.getId()).willReturn(itemId);
        return item;
    }

    private ReturnRequestDto.AdminProcessRequest approveWith(BigDecimal refundAmount) {
        ReturnRequestDto.AdminProcessRequest req = new ReturnRequestDto.AdminProcessRequest();
        req.setAction("APPROVE");
        req.setRefundAmount(refundAmount);
        return req;
    }

    @Nested
    @DisplayName("환불 금액")
    class RefundAmount {
        @Test
        @DisplayName("비워두면 주문 총액 전액이 환불된다")
        void nullMeansFullRefund() {
            pendingReturn("RETURN");

            var decision = transactionService.applyDecision(RETURN_NO, approveWith(null));

            assertThat(decision.refundAmount()).isEqualByComparingTo(ORDER_TOTAL);
            assertThat(decision.needsRefund()).isTrue();
        }

        @Test
        @DisplayName("주문 총액보다 크면 거절한다 — 숫자를 잘못 눌러도 돈이 더 나가면 안 된다")
        void overOrderTotalRejected() {
            pendingReturn("RETURN");

            assertThatThrownBy(() ->
                    transactionService.applyDecision(RETURN_NO, approveWith(new BigDecimal("100001"))))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("주문 총액과 같으면 통과한다 — 전액 환불은 정상이다")
        void exactlyOrderTotalAllowed() {
            pendingReturn("RETURN");

            var decision = transactionService.applyDecision(RETURN_NO, approveWith(ORDER_TOTAL));

            assertThat(decision.refundAmount()).isEqualByComparingTo(ORDER_TOTAL);
        }

        @Test
        @DisplayName("0 이하는 거절한다 — 환불할 게 없거나 거꾸로 청구하는 셈이다")
        void zeroOrNegativeRejected() {
            pendingReturn("RETURN");
            assertThatThrownBy(() ->
                    transactionService.applyDecision(RETURN_NO, approveWith(BigDecimal.ZERO)))
                    .isInstanceOf(BusinessException.class);

            pendingReturn("RETURN");
            assertThatThrownBy(() ->
                    transactionService.applyDecision(RETURN_NO, approveWith(new BigDecimal("-1000"))))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("부분 환불은 허용한다 — 일부만 돌려주는 경우가 있다")
        void partialRefundAllowed() {
            pendingReturn("RETURN");

            var decision = transactionService.applyDecision(RETURN_NO, approveWith(new BigDecimal("30000")));

            assertThat(decision.refundAmount()).isEqualByComparingTo(new BigDecimal("30000"));
        }
    }

    @Nested
    @DisplayName("처리 상태")
    class State {
        @Test
        @DisplayName("거절하면 환불 대상이 아니다")
        void rejectDoesNotRefund() {
            pendingReturn("RETURN");

            ReturnRequestDto.AdminProcessRequest req = new ReturnRequestDto.AdminProcessRequest();
            req.setAction("REJECT");

            var decision = transactionService.applyDecision(RETURN_NO, req);

            assertThat(decision.needsRefund()).isFalse();
            verify(stockService, never()).restoreByReturn(anyLong(), any(Integer.class), anyLong());
        }

        @Test
        @DisplayName("이미 처리된 건은 다시 처리할 수 없다 — 두 번 환불되면 안 된다")
        void alreadyProcessedRejected() {
            ReturnRequest request = pendingReturn("RETURN");
            request.approve(ORDER_TOTAL, "1차 승인");

            assertThatThrownBy(() ->
                    transactionService.applyDecision(RETURN_NO, approveWith(ORDER_TOTAL)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("APPROVE / REJECT 가 아닌 값은 거절한다")
        void unknownActionRejected() {
            pendingReturn("RETURN");

            ReturnRequestDto.AdminProcessRequest req = new ReturnRequestDto.AdminProcessRequest();
            req.setAction("CANCEL");

            assertThatThrownBy(() -> transactionService.applyDecision(RETURN_NO, req))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("재고 복구")
    class StockRestore {
        @Test
        @DisplayName("반품 승인 시 재고를 되돌린다")
        void returnRestoresStock() {
            OrderItem item = orderItem(10L, 2, 100L);
            given(order.getOrderItems()).willReturn(List.of(item));
            pendingReturn("RETURN");

            transactionService.applyDecision(RETURN_NO, approveWith(ORDER_TOTAL));

            verify(stockService).restoreByReturn(10L, 2, 100L);
        }

        @Test
        @DisplayName("교환은 승인 시점에 되돌리지 않는다 — 완료 시점에 한 번만 되돌린다")
        void exchangeDoesNotRestoreOnApproval() {
            OrderItem item = orderItem(10L, 2, 100L);
            given(order.getOrderItems()).willReturn(List.of(item));
            pendingReturn("EXCHANGE");

            transactionService.applyDecision(RETURN_NO, approveWith(ORDER_TOTAL));

            verify(stockService, never()).restoreByReturn(anyLong(), any(Integer.class), anyLong());
        }
    }
}
