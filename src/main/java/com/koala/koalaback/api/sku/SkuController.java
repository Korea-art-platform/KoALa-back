package com.koala.koalaback.api.sku;

import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "상품", description = "판매 중인 작품 조회. 재고 수량은 여기서 내려주지 않는다")
@RestController
@RequiredArgsConstructor
public class SkuController {
    private final SkuService skuService;

    @Operation(summary = "작품 목록", description = """
            판매 중인 작품만 돌려준다. genre(소분류)·mainCategory(대분류)로 거를 수 있고,
            둘 다 없으면 전체다. 기본 20건씩.

            거르는 일을 화면이 아니라 서버에서 한다. 화면은 한 번에 한 페이지만 받으므로
            받아 온 20개 안에서 걸러 봐야 뒤 페이지에 있는 작품은 세지 못해 "0점"으로
            보인다. 페이지 수도 걸러낸 뒤 기준으로 맞춰 돌려준다.
            """)
    @GetMapping("/api/v1/skus")
    public ApiResponse<PageResponse<SkuDto.SummaryResponse>> getSkus(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String mainCategory,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(skuService.getActiveSkus(genre, mainCategory, pageable));
    }

    @Operation(summary = "작품 상세", description = "skuCode 로 조회한다. 없으면 SKU_NOT_FOUND.")
    @GetMapping("/api/v1/skus/{skuCode}")
    public ApiResponse<SkuDto.DetailResponse> getSku(
            @PathVariable String skuCode) {
        return ApiResponse.ok(skuService.getSkuByCode(skuCode));
    }

    @Operation(summary = "소분류별 작품 수", description = """
            소분류 코드별로 판매 중인 작품이 몇 점인지 돌려준다. 스토어 필터가 작품 없는
            분류를 숨기는 데 쓴다.
            """)
    @GetMapping("/api/v1/skus/genre-counts")
    public ApiResponse<Map<String, Long>> getGenreCounts() {
        return ApiResponse.ok(skuService.getGenreCounts());
    }

    @Operation(summary = "대분류별 작품 수", description = """
            스토어 대분류 칩이 작품 없는 분류를 숨기려면 개수를 알아야 한다.
            """)
    @GetMapping("/api/v1/skus/main-category-counts")
    public ApiResponse<Map<String, Long>> getMainCategoryCounts() {
        return ApiResponse.ok(skuService.getMainCategoryCounts());
    }

    @Operation(summary = "360도 회전 프레임", description = """
            작품을 돌려 보는 데 쓰는 프레임 목록이다. 각도 오름차순으로 돌려준다.

            skuCode 별로 캐시한다(sku360frames). 한 상품을 보는 동안 여러 번 부르는데
            프레임 구성은 관리자가 다시 올리기 전까지 바뀌지 않는다. 상품 수정과 프레임
            재업로드 때 그 키만 비운다.
            """)
    @GetMapping("/api/v1/skus/{skuCode}/360-frames")
    public ApiResponse<SkuDto.FrameListResponse> get360Frames(@PathVariable String skuCode) {
        return ApiResponse.ok(skuService.get360Frames(skuCode));
    }

    @Operation(summary = "작가별 작품 목록", description = "해당 작가의 판매 중인 작품만. 기본 20건씩.")
    @GetMapping("/api/v1/artists/{artistCode}/skus")
    public ApiResponse<PageResponse<SkuDto.SummaryResponse>> getSkusByArtist(
            @PathVariable String artistCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(skuService.getSkusByArtist(artistCode, pageable));
    }
}
