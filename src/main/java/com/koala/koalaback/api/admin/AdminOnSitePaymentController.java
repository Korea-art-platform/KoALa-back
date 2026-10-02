package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.service.OnSitePaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "어드민 · 현장결제", description = "품목명·금액·작가로 결제 링크를 만든다")
@RestController
@RequestMapping("/admin/api/v1/onsite-payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOnSitePaymentController {
    private final OnSitePaymentService onSitePaymentService;

    @Operation(summary = "현장결제 만들기", description = """
            상품 등록 없이 품목명과 금액으로 주문을 만든다. 응답의 payToken 으로
            결제 링크(/pay/{payToken})를 연다. 결제가 끝나면 바로 배송 완료가 된다.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OnSitePaymentDto.Response> create(
            @Valid @RequestBody OnSitePaymentDto.CreateRequest req) {
        return ApiResponse.ok(onSitePaymentService.create(req));
    }

    @Operation(summary = "최근 현장결제 50건")
    @GetMapping
    public ApiResponse<List<OnSitePaymentDto.Response>> getRecent() {
        return ApiResponse.ok(onSitePaymentService.getRecent());
    }
}