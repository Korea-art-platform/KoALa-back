package com.koala.koalaback.api.app;

import com.koala.koalaback.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class AppVersionController {
    private static final String MIN_VERSION    = "1.0.0";
    private static final String LATEST_VERSION = "1.0.0";
    private static final String AOS_STORE_URL  = "https://play.google.com/store/apps/details?id=com.koala.app";
    private static final String IOS_STORE_URL  = "https://apps.apple.com/app/id000000000";

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
