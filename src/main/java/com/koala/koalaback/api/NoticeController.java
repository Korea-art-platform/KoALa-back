package com.koala.koalaback.api;

import com.koala.koalaback.domain.notice.dto.NoticeDto;
import com.koala.koalaback.domain.notice.service.NoticeService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "공지사항", description = "고객에게 보이는 공지")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @Operation(summary = "공지 목록", description = "고정된 공지가 앞에 오고, 그다음은 최신순이다.")
    @GetMapping
    public ApiResponse<List<NoticeDto.NoticeResponse>> getPublicNotices() {
        return ApiResponse.ok(noticeService.getPublicNotices());
    }

    @Operation(summary = "공지 상세", description = """
            내려둔 공지는 RESOURCE_NOT_FOUND 다. 목록에서 사라진 뒤에도 주소만 알면
            열리는 일이 없게 한다.
            """)
    @GetMapping("/{noticeCode}")
    public ApiResponse<NoticeDto.NoticeResponse> getPublicNotice(@PathVariable String noticeCode) {
        return ApiResponse.ok(noticeService.getPublicNotice(noticeCode));
    }
}
