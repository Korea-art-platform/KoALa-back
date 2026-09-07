package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.service.ReturnRequestService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 반품·교환", description = "신청 승인·거절과 완료 처리")
@RestController
@RequestMapping("/admin/api/v1/returns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnController {
    private final ReturnRequestService returnRequestService;

    @Operation(summary = "반품·교환 목록")
    @GetMapping
    public ApiResponse<PageResponse<ReturnRequestDto.ReturnResponse>> getReturns(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.ok(returnRequestService.getAdminReturnRequests(status, pageable));
    }

    @Operation(summary = "반품·교환 상세")
    @GetMapping("/{returnNo}")
    public ApiResponse<ReturnRequestDto.ReturnResponse> getReturn(
            @PathVariable String returnNo) {
        return ApiResponse.ok(returnRequestService.getAdminReturnDetail(returnNo));
    }

    @Operation(summary = "반품·교환 승인/거절", description = """
            판정을 먼저 짧은 트랜잭션으로 반영하고, 환불이 필요하면 그 뒤에 PG 를 부른다.
            트랜잭션을 걸지 않는 이유는 주문 취소와 같다 — 외부 호출 동안 잠금을 쥐지
            않는다.

            환불이 실패해도 예외를 밖으로 던지지 않는다. 대신 결제 이력에 실패를 남기고
            사람이 처리하게 둔다 — 여기서 터뜨리면 이미 반영된 승인 판정과 어긋난다.
            """)
    @PatchMapping("/{returnNo}/process")
    public ApiResponse<ReturnRequestDto.ReturnResponse> processReturn(
            @PathVariable String returnNo,
            @Valid @RequestBody ReturnRequestDto.AdminProcessRequest req) {
        return ApiResponse.ok(returnRequestService.processReturnRequest(returnNo, req));
    }

    @Operation(summary = "반품·교환 완료", description = """
            승인(APPROVED)된 건만 완료할 수 있다.

            교환이면 재고를 되돌린다. 반품은 되돌리지 않는다 — 교환은 물건이 돌아와 다시
            팔 수 있지만, 반품은 이 코드가 재고를 손대지 않는다.
            """)
    @PatchMapping("/{returnNo}/complete")
    public ApiResponse<ReturnRequestDto.ReturnResponse> completeReturn(
            @PathVariable String returnNo) {
        return ApiResponse.ok(returnRequestService.completeReturnRequest(returnNo));
    }
}
