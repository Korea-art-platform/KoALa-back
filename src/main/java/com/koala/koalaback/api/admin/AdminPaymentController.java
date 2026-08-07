package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentService paymentService;

    /**
     * 확인이 필요한 결제 목록.
     *
     * <p>PG 응답을 받지 못해 승인/취소 여부가 확정되지 않은 건들이다.
     * 이 목록이 비어 있지 않으면 PG 콘솔에서 실제 상태를 확인하고 수동 처리해야 한다.
     */
    @GetMapping("/attention")
    public ApiResponse<List<PaymentDto.PaymentResponse>> getPaymentsNeedingAttention() {
        return ApiResponse.ok(paymentService.getPaymentsNeedingAttention());
    }
}
