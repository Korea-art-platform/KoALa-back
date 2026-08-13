package com.koala.koalaback.domain.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettlementDto {

    /**
     * 한 작가의 한 달치 정산.
     *
     * @param confirmed 확정 여부 — false 면 지금 계산한 값이라 아직 바뀔 수 있다
     */
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

    /** 월 전체 요약 — 화면 상단에 보여줄 합계 */
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
