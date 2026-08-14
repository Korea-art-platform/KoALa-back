package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.pending-timeout-minutes:30}")
    private long timeoutMinutes;

    @Scheduled(
            fixedDelayString = "${order.expiry-scan-interval-ms:300000}",
            initialDelayString = "${order.expiry-scan-initial-delay-ms:60000}")
    public void releaseExpiredPendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Order> expired =
                orderRepository.findByOrderStatusAndCreatedAtBefore("PENDING_PAYMENT", threshold);
        if (expired.isEmpty()) return;

        log.info("[OrderExpiry] 만료 미결제 주문 {}건 처리 시작 (기준 {}분 경과)", expired.size(), timeoutMinutes);
        int success = 0;
        for (Order o : expired) {
            try {
                orderService.expirePendingOrder(o.getId());
                success++;
            } catch (Exception e) {
                log.error("[OrderExpiry] 주문 {} 만료 처리 실패: {}", o.getOrderNo(), e.getMessage(), e);
            }
        }
        log.info("[OrderExpiry] 처리 완료: {}/{}건 취소", success, expired.size());
    }
}
