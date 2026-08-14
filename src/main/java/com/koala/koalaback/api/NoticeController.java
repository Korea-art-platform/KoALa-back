package com.koala.koalaback.api;

import com.koala.koalaback.domain.notice.dto.NoticeDto;
import com.koala.koalaback.domain.notice.service.NoticeService;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<List<NoticeDto.NoticeResponse>> getPublicNotices() {
        return ApiResponse.ok(noticeService.getPublicNotices());
    }

    @GetMapping("/{noticeCode}")
    public ApiResponse<NoticeDto.NoticeResponse> getPublicNotice(@PathVariable String noticeCode) {
        return ApiResponse.ok(noticeService.getPublicNotice(noticeCode));
    }
}
