package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.service.ReturnRequestService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/v1/returns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReturnController {
    private final ReturnRequestService returnRequestService;

    @GetMapping
    public ApiResponse<PageResponse<ReturnRequestDto.ReturnResponse>> getReturns(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.ok(returnRequestService.getAdminReturnRequests(status, pageable));
    }

    @GetMapping("/{returnNo}")
    public ApiResponse<ReturnRequestDto.ReturnResponse> getReturn(
            @PathVariable String returnNo) {
        return ApiResponse.ok(returnRequestService.getAdminReturnDetail(returnNo));
    }

    @PatchMapping("/{returnNo}/process")
    public ApiResponse<ReturnRequestDto.ReturnResponse> processReturn(
            @PathVariable String returnNo,
            @Valid @RequestBody ReturnRequestDto.AdminProcessRequest req) {
        return ApiResponse.ok(returnRequestService.processReturnRequest(returnNo, req));
    }

    @PatchMapping("/{returnNo}/complete")
    public ApiResponse<ReturnRequestDto.ReturnResponse> completeReturn(
            @PathVariable String returnNo) {
        return ApiResponse.ok(returnRequestService.completeReturnRequest(returnNo));
    }
}
