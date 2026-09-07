package com.koala.koalaback.api.banner;

import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.service.BannerService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "배너", description = "메인 화면 배너")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {
    private final BannerService bannerService;

    @Operation(summary = "노출 중인 배너", description = """
            bannerType 별로 지금 시각에 걸려 있는 것만 내려간다. 노출 기간이 지났거나
            아직 시작하지 않은 배너는 빠진다 — 화면이 기간을 따지지 않아도 된다.
            """)
    @GetMapping
    public ApiResponse<List<BannerDto.BannerResponse>> getVisibleBanners(
            @RequestParam(defaultValue = "MAIN") String bannerType) {
        return ApiResponse.ok(bannerService.getVisibleBanners(bannerType));
    }
}
