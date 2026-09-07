package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.maintenance.ImageBackfillService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일회성 보정 작업. 평상시에는 쓰지 않는다.
 *
 * 축소본·캐시 헤더 채우기는 호출당 limit 개만 처리하고 nextToken 을 준다.
 * done 이 true 가 될 때까지 nextToken 을 넘겨 가며 반복 호출하면 된다.
 */
@Tag(name = "어드민 · 유지보수", description = "운영 중 한 번씩 돌리는 작업. local 프로파일에서는 뜨지 않는다")
@RestController
@RequestMapping("/admin/api/v1/maintenance")
@RequiredArgsConstructor
@Profile("!local")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaintenanceController {
    private final ImageBackfillService imageBackfillService;

    @Operation(summary = "이미지 축소본 생성", description = """
            이미 올라간 원본에서 축소본을 만든다. 한 번에 limit 개씩 처리하고 다음
            시작점(nextToken)을 돌려준다 — 전부 한 번에 돌리면 요청이 끝나지 않는다.
            받은 토큰으로 다시 불러 이어서 처리한다.

            prefix 로 대상 범위를 좁힐 수 있다.

            @Profile("!local") 이라 로컬에서는 이 엔드포인트가 등록되지 않는다.
            """)
    @PostMapping("/image-derivatives")
    public ApiResponse<ImageBackfillService.Result> backfillImages(
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String nextToken) {
        return ApiResponse.ok(imageBackfillService.run(prefix, limit, nextToken));
    }
}
