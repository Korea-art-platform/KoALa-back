package com.koala.koalaback.api.banner;

import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.service.BannerService;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class BannerController {
    private final BannerService bannerService;

    @GetMapping
    public ApiResponse<List<BannerDto.BannerResponse>> getVisibleBanners(
            @RequestParam(defaultValue = "MAIN") String bannerType) {
        return ApiResponse.ok(bannerService.getVisibleBanners(bannerType));
    }
}
