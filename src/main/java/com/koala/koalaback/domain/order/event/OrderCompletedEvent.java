package com.koala.koalaback.domain.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCompletedEvent(
        String eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,

        Long orderId,
        String orderNo,
        Long userId,

        String ordererName,
        String ordererEmail,

        BigDecimal productAmount,
        BigDecimal shippingAmount,
        BigDecimal totalAmount,

        List<Item> items
) {
    public static final String TOPIC = "order.completed";
    public static final String EVENT_TYPE = "order.completed";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record Item(String skuCode, String skuName, String artistName,
                       int quantity, BigDecimal lineAmount) {}

    public static OrderCompletedEvent of(Long orderId, String orderNo, Long userId,
                                         String ordererName, String ordererEmail,
                                         BigDecimal productAmount, BigDecimal shippingAmount,
                                         BigDecimal totalAmount, List<Item> items) {
        return new OrderCompletedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                orderId, orderNo, userId,
                ordererName, ordererEmail,
                productAmount, shippingAmount, totalAmount,
                items);
    }

    public String partitionKey() {
        return String.valueOf(orderId);
    }
}
