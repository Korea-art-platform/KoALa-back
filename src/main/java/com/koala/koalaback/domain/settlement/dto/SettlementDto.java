package com.koala.koalaback.domain.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettlementDto {
    @Schema(description = "작가 한 명의 월 정산. 확정 전에는 그 자리에서 계산한 값이고, "
            + "확정 뒤에는 저장된 값이라 나중에 주문이 바뀌어도 움직이지 않는다")
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

    @Schema(description = "한 달치 정산 요약과 작가별 내역")
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

    @Schema(description = "지급 완료 표시. 실제 송금 뒤 기록용이다")
    public record MarkPaidRequest(String memo) {}

    @Schema(description = "작가 수수료율 변경. 다음 계산부터 적용되고 이미 확정된 달은 바뀌지 않는다")
    public record ChangeCommissionRateRequest(BigDecimal commissionRate) {}
}
