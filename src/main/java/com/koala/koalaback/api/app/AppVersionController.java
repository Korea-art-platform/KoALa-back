package com.koala.koalaback.api.app;

import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "앱", description = "모바일 앱 버전 확인")
@RestController
@RequestMapping("/api/v1/app")
public class AppVersionController {
    private static final String MIN_VERSION    = "1.0.0";
    private static final String LATEST_VERSION = "1.0.0";
    private static final String AOS_STORE_URL  = "https://play.google.com/store/apps/details?id=com.koala.app";
    private static final String IOS_STORE_URL  = "https://apps.apple.com/app/id000000000";

    @Operation(summary = "앱 버전 확인", description = """
            앱이 켜질 때 최소 지원 버전과 최신 버전을 확인하는 데 쓴다.

            값이 상수로 박혀 있다 — 버전을 올리려면 배포해야 한다.
            """)
    @GetMapping("/version")
    public ApiResponse<VersionResponse> getVersion() {
        return ApiResponse.ok(new VersionResponse(
                MIN_VERSION,
                LATEST_VERSION,
                false,
                AOS_STORE_URL
        ));
    }

    public record VersionResponse(
            String minVersion,
            String latestVersion,
            boolean forceUpdate,
            String storeUrl
    ) {}
}
