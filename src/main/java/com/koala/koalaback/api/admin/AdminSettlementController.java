package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.settlement.dto.SettlementDto;
import com.koala.koalaback.domain.settlement.service.SettlementService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 작가 정산 어드민 API.
 *
 * <p>실제 송금은 여기서 하지 않는다. 얼마를 보내야 하는지 계산하고, 보냈다는 사실을
 * 기록할 뿐이다. 송금 자동화는 별개의 문제이고 훨씬 위험하다.
 */
@RestController
@RequestMapping("/admin/api/v1/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminSettlementController {

    private final SettlementService settlementService;

    /** 월 정산 조회 — 확정 전이면 지금 계산한 값, 확정 후면 스냅샷 */
    @GetMapping("/{periodYm}")
    public ApiResponse<SettlementDto.PeriodSummaryResponse> getPeriod(
            @PathVariable
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "정산 월은 YYYY-MM 형식이어야 합니다.")
            String periodYm) {
        return ApiResponse.ok(settlementService.getPeriod(periodYm));
    }

    /** 확정 — 이 시점의 금액이 굳는다. 되돌릴 수 없다 */
    @PostMapping("/{periodYm}/confirm")
    public ApiResponse<SettlementDto.PeriodSummaryResponse> confirm(
            @PathVariable
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "정산 월은 YYYY-MM 형식이어야 합니다.")
            String periodYm) {
        return ApiResponse.ok(settlementService.confirm(periodYm));
    }

    /** 지급 완료 기록 */
    @PatchMapping("/{settlementId}/paid")
    public ApiResponse<Void> markPaid(@PathVariable Long settlementId,
                                      @RequestBody(required = false) SettlementDto.MarkPaidRequest req) {
        settlementService.markPaid(settlementId, req != null ? req.memo() : null);
        return ApiResponse.ok();
    }

    /** 작가 수수료율 변경 — 이후 확정분부터 반영된다 */
    @PatchMapping("/artists/{artistId}/commission-rate")
    public ApiResponse<Void> changeCommissionRate(
            @PathVariable Long artistId,
            @Valid @RequestBody SettlementDto.ChangeCommissionRateRequest req) {
        settlementService.changeCommissionRate(artistId, req.commissionRate());
        return ApiResponse.ok();
    }
}
