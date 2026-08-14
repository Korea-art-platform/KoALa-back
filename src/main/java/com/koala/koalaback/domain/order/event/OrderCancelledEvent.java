package com.koala.koalaback.domain.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

    public String partitionKey() {
        return String.valueOf(orderId);
    }
}
