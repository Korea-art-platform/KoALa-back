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

@RestController
@RequestMapping("/admin/api/v1/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminSettlementController {
    private final SettlementService settlementService;

    @GetMapping("/{periodYm}")
    public ApiResponse<SettlementDto.PeriodSummaryResponse> getPeriod(
            @PathVariable
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "정산 월은 YYYY-MM 형식이어야 합니다.")
            String periodYm) {
        return ApiResponse.ok(settlementService.getPeriod(periodYm));
    }

    @PostMapping("/{periodYm}/confirm")
    public ApiResponse<SettlementDto.PeriodSummaryResponse> confirm(
            @PathVariable
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "정산 월은 YYYY-MM 형식이어야 합니다.")
            String periodYm) {
        return ApiResponse.ok(settlementService.confirm(periodYm));
    }

    @PatchMapping("/{settlementId}/paid")
    public ApiResponse<Void> markPaid(@PathVariable Long settlementId,
                                      @RequestBody(required = false) SettlementDto.MarkPaidRequest req) {
        settlementService.markPaid(settlementId, req != null ? req.memo() : null);
        return ApiResponse.ok();
    }

    @PatchMapping("/artists/{artistId}/commission-rate")
    public ApiResponse<Void> changeCommissionRate(
            @PathVariable Long artistId,
            @Valid @RequestBody SettlementDto.ChangeCommissionRateRequest req) {
        settlementService.changeCommissionRate(artistId, req.commissionRate());
        return ApiResponse.ok();
    }
}
