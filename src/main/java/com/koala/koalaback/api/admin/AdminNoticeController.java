package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.notice.dto.NoticeDto;
import com.koala.koalaback.domain.notice.service.NoticeService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "어드민 · 공지", description = "공지 등록·수정·노출")
@RestController
@RequestMapping("/admin/api/v1/notices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNoticeController {
    private final NoticeService noticeService;

    @Operation(summary = "공지 목록 (어드민)", description = """
            내려둔 공지까지 전부 보인다. 고정 공지가 앞, 그다음 최신순.
            """)
    @GetMapping
    public ApiResponse<List<NoticeDto.NoticeResponse>> getAllNotices() {
        return ApiResponse.ok(noticeService.getAllNotices());
    }

    @Operation(summary = "공지 상세 (어드민)", description = """
            고객용과 달리 내려둔 공지도 열린다.
            """)
    @GetMapping("/{noticeCode}")
    public ApiResponse<NoticeDto.NoticeResponse> getNotice(@PathVariable String noticeCode) {
        return ApiResponse.ok(noticeService.getNotice(noticeCode));
    }

    @Operation(summary = "공지 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NoticeDto.NoticeResponse> createNotice(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody NoticeDto.CreateRequest req) {
        return ApiResponse.ok(noticeService.createNotice(adminId, req));
    }

    @Operation(summary = "공지 수정")
    @PutMapping("/{noticeCode}")
    public ApiResponse<NoticeDto.NoticeResponse> updateNotice(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String noticeCode,
            @Valid @RequestBody NoticeDto.UpdateRequest req) {
        return ApiResponse.ok(noticeService.updateNotice(adminId, noticeCode, req));
    }

    @Operation(summary = "공지 노출")
    @PatchMapping("/{noticeCode}/activate")
    public ApiResponse<Void> activateNotice(@PathVariable String noticeCode) {
        noticeService.activateNotice(noticeCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "공지 내림", description = "고객 목록과 상세 양쪽에서 사라진다.")
    @PatchMapping("/{noticeCode}/deactivate")
    public ApiResponse<Void> deactivateNotice(@PathVariable String noticeCode) {
        noticeService.deactivateNotice(noticeCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "공지 삭제", description = "소프트 삭제다.")
    @DeleteMapping("/{noticeCode}")
    public ApiResponse<Void> deleteNotice(@PathVariable String noticeCode) {
        noticeService.deleteNotice(noticeCode);
        return ApiResponse.ok();
    }
}
