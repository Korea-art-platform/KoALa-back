package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "어드민 · 결제", description = "손이 필요한 결제 건")
@RestController
@RequestMapping("/admin/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {
    private final PaymentService paymentService;

    @Operation(summary = "확인이 필요한 결제", description = """
            IN_DOUBT · IN_PROGRESS · CANCEL_IN_PROGRESS 상태의 결제를 최근순으로 모은다.

            승인이나 취소가 끝내 확정되지 않은 건들이다. PG 콘솔에서 실제 상태를 확인한
            뒤 수동 종결로 닫는다.
            """)
    @GetMapping("/attention")
    public ApiResponse<List<PaymentDto.PaymentResponse>> getPaymentsNeedingAttention() {
        return ApiResponse.ok(paymentService.getPaymentsNeedingAttention());
    }
}
