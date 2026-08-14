package com.koala.koalaback.domain.payment.dto;

import java.math.BigDecimal;

public interface DailyRevenueProjection {
    String getDate();
    BigDecimal getRevenue();
    Long getOrderCount();
}
