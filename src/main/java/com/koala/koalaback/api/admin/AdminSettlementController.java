package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.settlement.dto.SettlementDto;
import com.koala.koalaback.domain.settlement.service.SettlementService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 정산", description = "작가별 월 정산")
@RestController
@RequestMapping("/admin/api/v1/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminSettlementController {
    private final SettlementService settlementService;

    @Operation(summary = "월 정산 조회", description = """
            periodYm 은 YYYY-MM 이다.

            확정 전이면 그 자리에서 계산해 보여주고, 확정된 달이면 저장된 값을 읽어
            보여준다. 확정 전 숫자는 볼 때마다 달라질 수 있다.

            지급액은 (매출 − 환불) 에서 수수료를 뺀 값이다. 수수료는 원 단위로 반올림한다.
            """)
    @GetMapping("/{periodYm}")
    public ApiResponse<SettlementDto.PeriodSummaryResponse> getPeriod(
            @PathVariable
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "정산 월은 YYYY-MM 형식이어야 합니다.")
            String periodYm) {
        return ApiResponse.ok(settlementService.getPeriod(periodYm));
    }

    @Operation(summary = "월 정산 확정", description = """
            그 달의 금액을 계산해 저장한다. 확정 뒤에는 조회가 저장된 값을 읽으므로,
            나중에 주문이 바뀌어도 확정된 정산은 움직이지 않는다.

            아직 끝나지 않은 달은 확정할 수 없다. 이미 확정된 달도 다시 확정할 수 없다.

            지급액이 0 이하인 작가는 확정에서 빠진다.
            """)
    @PostMapping("/{periodYm}/confirm")
    public ApiResponse<SettlementDto.PeriodSummaryResponse> confirm(
            @PathVariable
            @Pattern(regexp = "\\d{4}-\\d{2}", message = "정산 월은 YYYY-MM 형식이어야 합니다.")
            String periodYm) {
        return ApiResponse.ok(settlementService.confirm(periodYm));
    }

    @Operation(summary = "지급 완료 표시", description = "실제 송금 뒤 기록용이다. 메모를 남길 수 있다.")
    @PatchMapping("/{settlementId}/paid")
    public ApiResponse<Void> markPaid(@PathVariable Long settlementId,
                                      @RequestBody(required = false) SettlementDto.MarkPaidRequest req) {
        settlementService.markPaid(settlementId, req != null ? req.memo() : null);
        return ApiResponse.ok();
    }

    @Operation(summary = "작가 수수료율 변경", description = """
            다음 계산부터 적용된다. 이미 확정된 달은 그때의 수수료율이 저장돼 있어
            바뀌지 않는다.
            """)
    @PatchMapping("/artists/{artistId}/commission-rate")
    public ApiResponse<Void> changeCommissionRate(
            @PathVariable Long artistId,
            @Valid @RequestBody SettlementDto.ChangeCommissionRateRequest req) {
        settlementService.changeCommissionRate(artistId, req.commissionRate());
        return ApiResponse.ok();
    }
}
