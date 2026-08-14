package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.service.BannerService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/v1/banners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBannerController {
    private final BannerService bannerService;

    @GetMapping
    public ApiResponse<List<BannerDto.BannerResponse>> getAllBanners() {
        return ApiResponse.ok(bannerService.getAllBanners());
    }

    @GetMapping("/{bannerCode}")
    public ApiResponse<BannerDto.BannerResponse> getBanner(
            @PathVariable String bannerCode) {
        return ApiResponse.ok(bannerService.getBanner(bannerCode));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BannerDto.BannerResponse> createBanner(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody BannerDto.CreateRequest req) {
        return ApiResponse.ok(bannerService.createBanner(adminId, req));
    }

    @PutMapping("/{bannerCode}")
    public ApiResponse<BannerDto.BannerResponse> updateBanner(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String bannerCode,
            @Valid @RequestBody BannerDto.UpdateRequest req) {
        return ApiResponse.ok(bannerService.updateBanner(adminId, bannerCode, req));
    }

    @PatchMapping("/{bannerCode}/activate")
    public ApiResponse<Void> activateBanner(@PathVariable String bannerCode) {
        bannerService.activateBanner(bannerCode);
        return ApiResponse.ok();
    }

    @PatchMapping("/{bannerCode}/deactivate")
    public ApiResponse<Void> deactivateBanner(@PathVariable String bannerCode) {
        bannerService.deactivateBanner(bannerCode);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{bannerCode}")
    public ApiResponse<Void> deleteBanner(@PathVariable String bannerCode) {
        bannerService.deleteBanner(bannerCode);
        return ApiResponse.ok();
    }

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> uploadBannerImage(
            @RequestPart("file") MultipartFile file) {
        String url = bannerService.uploadImage(file);
        return ApiResponse.ok(Map.of("imageUrl", url));
    }

    @PatchMapping(value = "/{bannerCode}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BannerDto.BannerResponse> updateBannerImage(
            @PathVariable String bannerCode,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(bannerService.updateImage(bannerCode, file));
    }
}
