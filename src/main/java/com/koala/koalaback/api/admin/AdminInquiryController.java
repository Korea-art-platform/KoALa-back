package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.inquiry.dto.InquiryDto;
import com.koala.koalaback.domain.inquiry.service.InquiryService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/v1/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {
    private final InquiryService inquiryService;

    @GetMapping
    public ApiResponse<PageResponse<InquiryDto.InquiryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inquiryService.getAllInquiries(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/{inquiryCode}")
    public ApiResponse<InquiryDto.InquiryResponse> detail(@PathVariable String inquiryCode) {
        return ApiResponse.ok(inquiryService.getInquiry(inquiryCode));
    }

    @PostMapping("/{inquiryCode}/answer")
    public ApiResponse<InquiryDto.InquiryResponse> answer(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String inquiryCode,
            @Valid @RequestBody InquiryDto.AnswerRequest req) {
        return ApiResponse.ok(inquiryService.answerInquiry(adminId, inquiryCode, req));
    }

    @PatchMapping("/{inquiryCode}/close")
    public ApiResponse<Void> close(@PathVariable String inquiryCode) {
        inquiryService.closeInquiry(inquiryCode);
        return ApiResponse.ok();
    }
}
