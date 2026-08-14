package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
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
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StockService stockService;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
    public OrderDto.OrderDetailResponse completeCancel(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.isCancellable()) {
            log.error("환불 후 주문 취소 불가 상태 — 수동 확인 필요: orderNo={}, status={}",
                    orderNo, order.getOrderStatus());
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        lockOrderedItems(order).forEach(item ->
                stockService.restore(item.getSku().getId(), item.getQuantity(),
                        "order_items", item.getId()));

        order.cancel();
        log.info("Order cancelled: orderNo={}, userId={}", orderNo, userId);

        publishCancelled(order, "USER", "사용자 주문취소", order.getTotalAmount());

        return OrderDto.OrderDetailResponse.from(order);
    }

    @Transactional
    public String forceCancelAndFindRefundTarget(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        lockOrderedItems(order).forEach(item ->
                stockService.restore(item.getSku().getId(), item.getQuantity(),
                        "admin_cancel", item.getId()));

        order.forceCancel();

        publishCancelled(order, "ADMIN", "관리자 강제취소", order.getTotalAmount());

        return paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> "CAPTURED".equals(p.getStatus()))
                .map(p -> p.getPaymentNo())
                .orElse(null);
    }

    private List<OrderItem> lockOrderedItems(Order order) {
        return order.getOrderItems().stream()
                .filter(item -> item.getSku() != null)
                .sorted(Comparator.comparing(item -> item.getSku().getId()))
                .toList();
    }

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
