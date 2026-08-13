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

/**
 * 배송완료 자동 전환.
 *
 * <p>지금까지 배송완료는 <b>주문마다 어드민에서 눌러야</b> 했다. 누르지 않으면 상태가
 * SHIPPED 에 머물고, 그러면 고객이 반품을 신청할 수 없다 — 반품은 DELIVERED 부터만 가능하다.
 * 즉 이 클릭을 잊는 것은 단순히 화면이 안 맞는 문제가 아니라 <b>고객이 반품을 못 하는</b> 문제다.
 *
 * <h3>꺼져 있는 것이 기본이다</h3>
 * <p>조회 API 키가 없으면 {@link DeliveryTracker} 빈이 없고, 이 스케줄러도 뜨지 않는다.
 * 그 상태에서는 지금처럼 손으로 누르면 된다.
 *
 * <h3>UNKNOWN 은 건드리지 않는다</h3>
 * <p>조회 실패와 "배송 안 됨"은 다르다. 확실히 DELIVERED 일 때만 상태를 바꾸고,
 * 나머지는 다음 주기에 다시 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "koala.delivery.tracking.enabled", havingValue = "true")
public class DeliveryTrackingScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final DeliveryTracker deliveryTracker;

    /** 이 기간이 지나도 배송완료로 잡히지 않으면 폴링을 멈춘다 (기본 30일) */
    @Value("${koala.delivery.tracking.max-age-days:30}")
    private long maxAgeDays;

    /**
     * 배송 중 주문을 훑는다 — 기본 30분 주기.
     *
     * <p>더 자주 돌 이유가 없다. 택배사 스캔 자체가 몇 시간 단위로 올라오고,
     * 조회 API 는 호출 수에 한도가 있다.
     */
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
                // 한 건이 실패해도 나머지는 계속 본다
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

        // 주문별 독립 트랜잭션 — 별도 빈 호출이라 @Transactional 이 정상 적용된다
        orderService.markDelivered(order.getOrderNo());
        log.info("[DeliveryTracking] 배송완료 전환: orderNo={}", order.getOrderNo());
        return true;
    }
}
