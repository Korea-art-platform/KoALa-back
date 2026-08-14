package com.koala.koalaback.api.inquiry;

import com.koala.koalaback.domain.inquiry.dto.InquiryDto;
import com.koala.koalaback.domain.inquiry.service.InquiryService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class InquiryController {
    private final InquiryService inquiryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InquiryDto.InquiryResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InquiryDto.CreateRequest req) {
        return ApiResponse.ok(inquiryService.createInquiry(userId, req));
    }

    @GetMapping
    public ApiResponse<PageResponse<InquiryDto.InquiryResponse>> myList(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(inquiryService.getMyInquiries(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/{inquiryCode}")
    public ApiResponse<InquiryDto.InquiryResponse> myDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable String inquiryCode) {
        return ApiResponse.ok(inquiryService.getMyInquiry(userId, inquiryCode));
    }

    @DeleteMapping("/{inquiryCode}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable String inquiryCode) {
        inquiryService.deleteMyInquiry(userId, inquiryCode);
        return ApiResponse.ok();
    }
}
