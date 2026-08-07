package com.koala.koalaback.domain.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 주문 취소 이벤트 — 토픽 {@code order.cancelled}
 *
 * <p>스키마 규칙은 {@link OrderCompletedEvent} 와 동일하다(추가만 허용, schemaVersion 동반).
 * 파티션 키도 orderId 를 써서 같은 주문의 완료·취소 이벤트가
 * 같은 파티션에 순서대로 쌓이도록 한다.
 *
 * @param cancelType USER(사용자 취소) / ADMIN(관리자 강제취소) / EXPIRY(미결제 만료)
 */
public record OrderCancelledEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,

        Long orderId,
        String orderNo,
        Long userId,

        String cancelType,
        String reason,
        BigDecimal refundAmount
) {
    public static final String TOPIC = "order.cancelled";
    public static final String EVENT_TYPE = "order.cancelled";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static OrderCancelledEvent of(Long orderId, String orderNo, Long userId,
                                         String cancelType, String reason, BigDecimal refundAmount) {
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                orderId, orderNo, userId,
                cancelType, reason, refundAmount);
    }

    /** 파티션 키 — 주문 단위 순서 보장 */
    public String partitionKey() {
        return String.valueOf(orderId);
    }
}
