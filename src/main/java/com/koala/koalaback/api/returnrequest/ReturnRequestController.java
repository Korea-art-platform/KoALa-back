package com.koala.koalaback.api.returnrequest;

import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.service.ReturnRequestService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "반품·교환", description = "배송이 끝난 주문에 신청한다")
@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnRequestController {
    private final ReturnRequestService returnRequestService;

    @Operation(summary = "반품·교환 신청", description = """
            배송완료(DELIVERED) 상태의 내 주문만 신청할 수 있다.

            한 주문에 살아 있는 신청은 하나뿐이다. 거절(REJECTED)된 건은 세지 않으므로,
            거절당한 뒤에는 다시 신청할 수 있다.

            신청하면 관리자에게 알림이 나간다.
            """)
    @PostMapping
    public ApiResponse<ReturnRequestDto.ReturnResponse> createReturn(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ReturnRequestDto.CreateRequest req) {
        return ApiResponse.ok(returnRequestService.createReturnRequest(userId, req));
    }

    @Operation(summary = "내 반품·교환 목록")
    @GetMapping
    public ApiResponse<List<ReturnRequestDto.ReturnResponse>> getMyReturns(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(returnRequestService.getMyReturnRequests(userId));
    }

    @Operation(summary = "주문번호로 반품 조회", description = """
            주문 상세 화면이 '반품 신청됨' 을 표시할지 정하는 데 쓴다.
            """)
    @GetMapping("/order/{orderNo}")
    public ApiResponse<ReturnRequestDto.ReturnResponse> getReturnByOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(returnRequestService.getMyReturnByOrderNo(userId, orderNo));
    }
}
