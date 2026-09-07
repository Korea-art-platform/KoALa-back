package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.inquiry.dto.InquiryDto;
import com.koala.koalaback.domain.inquiry.service.InquiryService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 문의", description = "1:1 문의 답변")
@RestController
@RequestMapping("/admin/api/v1/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {
    private final InquiryService inquiryService;

    @Operation(summary = "문의 목록", description = "status 로 거를 수 있다.")
    @GetMapping
    public ApiResponse<PageResponse<InquiryDto.InquiryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inquiryService.getAllInquiries(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Operation(summary = "문의 상세", description = "비밀글도 열린다.")
    @GetMapping("/{inquiryCode}")
    public ApiResponse<InquiryDto.InquiryResponse> detail(@PathVariable String inquiryCode) {
        return ApiResponse.ok(inquiryService.getInquiry(inquiryCode));
    }

    @Operation(summary = "문의 답변", description = """
            답변이 달리면 고객은 그 문의를 지울 수 없게 된다.
            """)
    @PostMapping("/{inquiryCode}/answer")
    public ApiResponse<InquiryDto.InquiryResponse> answer(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String inquiryCode,
            @Valid @RequestBody InquiryDto.AnswerRequest req) {
        return ApiResponse.ok(inquiryService.answerInquiry(adminId, inquiryCode, req));
    }

    @Operation(summary = "문의 종료")
    @PatchMapping("/{inquiryCode}/close")
    public ApiResponse<Void> close(@PathVariable String inquiryCode) {
        inquiryService.closeInquiry(inquiryCode);
        return ApiResponse.ok();
    }
}
