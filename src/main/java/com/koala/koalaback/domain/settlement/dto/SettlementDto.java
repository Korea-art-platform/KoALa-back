package com.koala.koalaback.domain.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettlementDto {
    public record ArtistSettlementResponse(
            Long settlementId,
            Long artistId,
            String artistName,
            String periodYm,
            BigDecimal grossAmount,
            BigDecimal refundAmount,
            BigDecimal netAmount,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal payoutAmount,
            boolean confirmed,
            String status,
            LocalDateTime paidAt,
            String memo
    ) {}

    public record PeriodSummaryResponse(
            String periodYm,
            boolean confirmed,
            int artistCount,
            BigDecimal totalGross,
            BigDecimal totalRefund,
            BigDecimal totalCommission,
            BigDecimal totalPayout,
            java.util.List<ArtistSettlementResponse> items
    ) {}

    public record MarkPaidRequest(String memo) {}

    public record ChangeCommissionRateRequest(BigDecimal commissionRate) {}
}
