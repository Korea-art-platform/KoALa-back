package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.infra.delivery.DeliveryTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "koala.delivery.tracking.enabled", havingValue = "true")
public class DeliveryTrackingScheduler {
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final DeliveryTracker deliveryTracker;

    @Value("${koala.delivery.tracking.max-age-days:30}")
    private long maxAgeDays;

    @Scheduled(
            fixedDelayString = "${koala.delivery.tracking.scan-interval-ms:1800000}",
            initialDelayString = "${koala.delivery.tracking.scan-initial-delay-ms:120000}")
    public void syncDeliveredOrders() {
        LocalDateTime since = LocalDateTime.now().minusDays(maxAgeDays);
        List<Order> shipped = orderRepository.findShippedWithTrackingSince(since);
        if (shipped.isEmpty()) return;

        log.info("[DeliveryTracking] 배송 중 주문 {}건 조회 시작", shipped.size());
        int delivered = 0;

        for (Order order : shipped) {
            try {
                if (markDeliveredIfCompleted(order)) delivered++;
            } catch (Exception e) {
                log.warn("[DeliveryTracking] 처리 실패: orderNo={}, error={}",
                        order.getOrderNo(), e.getMessage());
            }
        }

        if (delivered > 0) {
            log.info("[DeliveryTracking] 배송완료 전환 {}건", delivered);
        }
    }

    private boolean markDeliveredIfCompleted(Order order) {
        OrderShipment shipment = order.getShipment();
        if (shipment == null) return false;

        DeliveryTracker.Status status =
                deliveryTracker.track(shipment.getCarrierCode(), shipment.getTrackingNo());

        if (status != DeliveryTracker.Status.DELIVERED) return false;

        orderService.markDelivered(order.getOrderNo());
        log.info("[DeliveryTracking] 배송완료 전환: orderNo={}", order.getOrderNo());
        return true;
    }
}
