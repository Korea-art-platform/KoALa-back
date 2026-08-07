package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.event.OrderCancelledEvent;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 주문 취소 흐름의 <b>DB 단계</b>만 담당한다. 외부 PG 호출은 들어오지 않는다.
 *
 * <p>{@link OrderService} 안에 두면 자기호출(self-invocation)이라 Spring 프록시를 타지 않아
 * {@code @Transactional} 이 무시된다. 그러면 "환불을 트랜잭션 밖에서" 라는 목적이 깨지므로
 * 별도 빈으로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StockService stockService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ① 취소 가능 여부 확인 + 환불 대상 결제 조회.
     *
     * @return 환불해야 할 결제번호. 환불할 결제가 없으면 {@code null}
     */
    @Transactional(readOnly = true)
    public String checkCancellable(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.isCancellable()) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        return paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> "CAPTURED".equals(p.getStatus()))
                .map(p -> p.getPaymentNo())
                .orElse(null);
    }

    /**
     * ③ 재고 복구 + 주문 취소.
     *
     * <p>①과 ③ 사이에 트랜잭션이 끊기므로 그 사이 주문 상태가 바뀌었을 수 있다.
     * 여기서 취소 가능 여부를 다시 확인한다(중복 취소 방지).
     */
    @Transactional
    public OrderDto.OrderDetailResponse completeCancel(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.isCancellable()) {
            // 환불은 이미 나갔는데 주문이 취소 불가 상태 — 수동 확인이 필요하다
            log.error("환불 후 주문 취소 불가 상태 — 수동 확인 필요: orderNo={}, status={}",
                    orderNo, order.getOrderStatus());
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        order.getOrderItems().forEach(item -> {
            if (item.getSku() != null) {
                stockService.restore(item.getSku().getId(), item.getQuantity(),
                        "order_items", item.getId());
            }
        });

        order.cancel();
        log.info("Order cancelled: orderNo={}, userId={}", orderNo, userId);

        publishCancelled(order, "USER", "사용자 주문취소", order.getTotalAmount());

        return OrderDto.OrderDetailResponse.from(order);
    }

    /**
     * 관리자 강제 취소의 DB 단계 — 재고 복구 + 주문 강제 취소.
     *
     * @return 환불해야 할 결제번호. 없으면 {@code null}
     */
    @Transactional
    public String forceCancelAndFindRefundTarget(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        order.getOrderItems().forEach(item -> {
            if (item.getSku() != null) {
                stockService.restore(item.getSku().getId(), item.getQuantity(),
                        "admin_cancel", item.getId());
            }
        });

        order.forceCancel();

        publishCancelled(order, "ADMIN", "관리자 강제취소", order.getTotalAmount());

        return paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> "CAPTURED".equals(p.getStatus()))
                .map(p -> p.getPaymentNo())
                .orElse(null);
    }

    /**
     * 취소 이벤트 발행 — 실제 전달은 커밋 후 {@code OrderEventRelay} 가 한다.
     * 커밋 전에 내보내면 롤백 시 유령 이벤트가 된다.
     */
    private void publishCancelled(Order order, String cancelType, String reason, BigDecimal refundAmount) {
        eventPublisher.publishEvent(OrderCancelledEvent.of(
                order.getId(),
                order.getOrderNo(),
                order.getUser() != null ? order.getUser().getId() : null,
                cancelType, reason, refundAmount));
    }

    @Transactional(readOnly = true)
    public OrderDto.OrderDetailResponse getOrderDetail(String orderNo) {
        return OrderDto.OrderDetailResponse.from(
                orderRepository.findByOrderNo(orderNo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND)));
    }
}
