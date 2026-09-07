package com.koala.koalaback.api.inquiry;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "문의", description = "1:1 문의. 주문에 붙일 수도 있고 그냥 물어볼 수도 있다")
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class InquiryController {
    private final InquiryService inquiryService;

    @Operation(summary = "문의 등록", description = """
            orderNo 는 선택이다. 주문에 대한 문의면 붙이고, 아니면 비운다. 붙일 때는
            내 주문인지 확인한다.

            isSecret 을 켜면 비밀글이 된다.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InquiryDto.InquiryResponse> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InquiryDto.CreateRequest req) {
        return ApiResponse.ok(inquiryService.createInquiry(userId, req));
    }

    @Operation(summary = "내 문의 목록")
    @GetMapping
    public ApiResponse<PageResponse<InquiryDto.InquiryResponse>> myList(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(inquiryService.getMyInquiries(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Operation(summary = "내 문의 상세", description = "내가 쓴 것만 열린다. 아니면 FORBIDDEN.")
    @GetMapping("/{inquiryCode}")
    public ApiResponse<InquiryDto.InquiryResponse> myDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable String inquiryCode) {
        return ApiResponse.ok(inquiryService.getMyInquiry(userId, inquiryCode));
    }

    @Operation(summary = "내 문의 삭제", description = """
            답변이 달린 문의는 지울 수 없다. 답변한 쪽의 기록이 함께 사라진다.
            """)
    @DeleteMapping("/{inquiryCode}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable String inquiryCode) {
        inquiryService.deleteMyInquiry(userId, inquiryCode);
        return ApiResponse.ok();
    }
}
