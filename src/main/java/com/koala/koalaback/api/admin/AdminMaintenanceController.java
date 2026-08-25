package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.maintenance.ImageBackfillService;
import com.koala.koalaback.global.response.ApiResponse;
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
@RestController
@RequestMapping("/admin/api/v1/maintenance")
@RequiredArgsConstructor
@Profile("!local")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaintenanceController {
    private final ImageBackfillService imageBackfillService;

    @PostMapping("/image-derivatives")
    public ApiResponse<ImageBackfillService.Result> backfillImages(
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String nextToken) {
        return ApiResponse.ok(imageBackfillService.run(prefix, limit, nextToken));
    }
}
