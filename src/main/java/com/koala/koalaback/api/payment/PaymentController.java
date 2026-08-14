package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/api/v1/payments/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentDto.PrepareResponse> prepare(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaymentDto.PrepareRequest req) {
        return ApiResponse.ok(paymentService.prepare(userId, req));
    }

    @PostMapping("/api/v1/payments/confirm")
    public ApiResponse<PaymentDto.PaymentResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaymentDto.ConfirmRequest req) {
        return ApiResponse.ok(paymentService.confirm(userId, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/api/v1/payments/{paymentNo}/cancel")
    public ApiResponse<PaymentDto.PaymentResponse> cancel(
            @PathVariable String paymentNo,
            @Valid @RequestBody PaymentDto.CancelRequest req) {
        return ApiResponse.ok(paymentService.cancel(paymentNo, req));
    }
}
