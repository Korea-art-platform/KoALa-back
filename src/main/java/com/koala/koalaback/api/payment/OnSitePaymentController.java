package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.service.OnSitePaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "현장결제", description = "결제 링크를 연 고객이 보는 정보")
@RestController
@RequiredArgsConstructor
public class OnSitePaymentController {
    private final OnSitePaymentService onSitePaymentService;

    @Operation(summary = "현장결제 링크 정보", description = """
            품목명·작가·금액과 결제 가능 여부만 돌려준다. 결제는 기존
            /api/v1/payments/prepare 로 이어진다.
            """)
    @GetMapping("/api/v1/onsite-payments/{payToken}")
    public ApiResponse<OnSitePaymentDto.PublicResponse> get(@PathVariable String payToken) {
        return ApiResponse.ok(onSitePaymentService.getByToken(payToken));
    }
}