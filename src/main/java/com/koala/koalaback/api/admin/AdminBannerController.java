package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.service.BannerService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "어드민 · 배너", description = "메인 배너 등록·노출 기간·이미지")
@RestController
@RequestMapping("/admin/api/v1/banners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBannerController {
    private final BannerService bannerService;

    @Operation(summary = "배너 목록 (어드민)", description = """
            노출 기간과 무관하게 삭제되지 않은 배너를 전부, 정렬 순서대로 내려준다.
            고객 화면과 달리 지금 안 걸리는 배너도 봐야 고칠 수 있다.
            """)
    @GetMapping
    public ApiResponse<List<BannerDto.BannerResponse>> getAllBanners() {
        return ApiResponse.ok(bannerService.getAllBanners());
    }

    @Operation(summary = "배너 상세")
    @GetMapping("/{bannerCode}")
    public ApiResponse<BannerDto.BannerResponse> getBanner(
            @PathVariable String bannerCode) {
        return ApiResponse.ok(bannerService.getBanner(bannerCode));
    }

    @Operation(summary = "배너 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BannerDto.BannerResponse> createBanner(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody BannerDto.CreateRequest req) {
        return ApiResponse.ok(bannerService.createBanner(adminId, req));
    }

    @Operation(summary = "배너 수정")
    @PutMapping("/{bannerCode}")
    public ApiResponse<BannerDto.BannerResponse> updateBanner(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String bannerCode,
            @Valid @RequestBody BannerDto.UpdateRequest req) {
        return ApiResponse.ok(bannerService.updateBanner(adminId, bannerCode, req));
    }

    @Operation(summary = "배너 활성화", description = "노출 기간 안이면 고객 화면에 걸린다.")
    @PatchMapping("/{bannerCode}/activate")
    public ApiResponse<Void> activateBanner(@PathVariable String bannerCode) {
        bannerService.activateBanner(bannerCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "배너 비활성화")
    @PatchMapping("/{bannerCode}/deactivate")
    public ApiResponse<Void> deactivateBanner(@PathVariable String bannerCode) {
        bannerService.deactivateBanner(bannerCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "배너 삭제", description = "소프트 삭제다.")
    @DeleteMapping("/{bannerCode}")
    public ApiResponse<Void> deleteBanner(@PathVariable String bannerCode) {
        bannerService.deleteBanner(bannerCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "배너 이미지 업로드", description = """
            파일만 올리고 주소를 돌려준다. 배너에 붙이지는 않는다 — 등록·수정 요청에
            그 주소를 실어 보내는 방식이다.
            """)
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> uploadBannerImage(
            @RequestPart("file") MultipartFile file) {
        String url = bannerService.uploadImage(file);
        return ApiResponse.ok(Map.of("imageUrl", url));
    }

    @Operation(summary = "배너 이미지 교체", description = """
            올리면서 바로 배너에 붙인다. 기존 이미지는 저장소에서 지운다 — 바뀐 이미지가
            계속 쌓이면 아무도 안 쓰는 파일이 남는다.
            """)
    @PatchMapping(value = "/{bannerCode}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BannerDto.BannerResponse> updateBannerImage(
            @PathVariable String bannerCode,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(bannerService.updateImage(bannerCode, file));
    }
}
