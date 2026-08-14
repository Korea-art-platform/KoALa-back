package com.koala.koalaback.domain.sku.event;

public record StockDepletedEvent(
        String skuCode,
        String skuName,
        String artistName
) {}
