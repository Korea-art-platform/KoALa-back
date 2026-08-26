package com.koala.koalaback.api.sku;

import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SkuController {
    private final SkuService skuService;

    @GetMapping("/api/v1/skus")
    public ApiResponse<PageResponse<SkuDto.SummaryResponse>> getSkus(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String mainCategory,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(skuService.getActiveSkus(genre, mainCategory, pageable));
    }

    @GetMapping("/api/v1/skus/{skuCode}")
    public ApiResponse<SkuDto.DetailResponse> getSku(
            @PathVariable String skuCode) {
        return ApiResponse.ok(skuService.getSkuByCode(skuCode));
    }

    @GetMapping("/api/v1/skus/genre-counts")
    public ApiResponse<Map<String, Long>> getGenreCounts() {
        return ApiResponse.ok(skuService.getGenreCounts());
    }

    @GetMapping("/api/v1/skus/main-category-counts")
    public ApiResponse<Map<String, Long>> getMainCategoryCounts() {
        return ApiResponse.ok(skuService.getMainCategoryCounts());
    }

    @GetMapping("/api/v1/skus/{skuCode}/360-frames")
    public ApiResponse<SkuDto.FrameListResponse> get360Frames(@PathVariable String skuCode) {
        return ApiResponse.ok(skuService.get360Frames(skuCode));
    }

    @GetMapping("/api/v1/artists/{artistCode}/skus")
    public ApiResponse<PageResponse<SkuDto.SummaryResponse>> getSkusByArtist(
            @PathVariable String artistCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(skuService.getSkusByArtist(artistCode, pageable));
    }
}
