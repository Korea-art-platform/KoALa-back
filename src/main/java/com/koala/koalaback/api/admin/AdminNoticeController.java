package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.notice.dto.NoticeDto;
import com.koala.koalaback.domain.notice.service.NoticeService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/notices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminNoticeController {
    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<List<NoticeDto.NoticeResponse>> getAllNotices() {
        return ApiResponse.ok(noticeService.getAllNotices());
    }

    @GetMapping("/{noticeCode}")
    public ApiResponse<NoticeDto.NoticeResponse> getNotice(@PathVariable String noticeCode) {
        return ApiResponse.ok(noticeService.getNotice(noticeCode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NoticeDto.NoticeResponse> createNotice(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody NoticeDto.CreateRequest req) {
        return ApiResponse.ok(noticeService.createNotice(adminId, req));
    }

    @PutMapping("/{noticeCode}")
    public ApiResponse<NoticeDto.NoticeResponse> updateNotice(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String noticeCode,
            @Valid @RequestBody NoticeDto.UpdateRequest req) {
        return ApiResponse.ok(noticeService.updateNotice(adminId, noticeCode, req));
    }

    @PatchMapping("/{noticeCode}/activate")
    public ApiResponse<Void> activateNotice(@PathVariable String noticeCode) {
        noticeService.activateNotice(noticeCode);
        return ApiResponse.ok();
    }

    @PatchMapping("/{noticeCode}/deactivate")
    public ApiResponse<Void> deactivateNotice(@PathVariable String noticeCode) {
        noticeService.deactivateNotice(noticeCode);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{noticeCode}")
    public ApiResponse<Void> deleteNotice(@PathVariable String noticeCode) {
        noticeService.deleteNotice(noticeCode);
        return ApiResponse.ok();
    }
}
