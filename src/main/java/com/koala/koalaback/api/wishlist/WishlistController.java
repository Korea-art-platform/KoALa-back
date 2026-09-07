package com.koala.koalaback.api.wishlist;

import com.koala.koalaback.domain.wishlist.dto.WishlistDto;
import com.koala.koalaback.domain.wishlist.service.WishlistService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "찜", description = "관심 작품 담기·해제·확인")
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {
    private final WishlistService wishlistService;

    @Operation(summary = "찜 목록", description = "최근에 찜한 것이 앞에 온다. 기본 20건씩.")
    @GetMapping
    public ApiResponse<PageResponse<WishlistDto.WishlistItemResponse>> getWishlist(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(wishlistService.getWishlist(userId, pageable));
    }

    @Operation(summary = "찜하기", description = """
            이미 찜한 작품이면 DUPLICATE_RESOURCE 로 거절한다.

            해제와 대칭이 아니다 — 찜하기는 중복이면 오류를 내고, 해제는 없는 것을
            지워도 조용히 넘어간다.
            """)
    @PostMapping("/{skuCode}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WishlistDto.WishlistItemResponse> addToWishlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable String skuCode) {
        return ApiResponse.ok(wishlistService.addToWishlist(userId, skuCode));
    }

    @Operation(summary = "찜 해제", description = """
            존재 여부를 확인하지 않고 지운다. 찜하지 않은 작품에 불러도 오류가 아니고,
            여러 번 불러도 결과가 같다.
            """)
    @DeleteMapping("/{skuCode}")
    public ApiResponse<Void> removeFromWishlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable String skuCode) {
        wishlistService.removeFromWishlist(userId, skuCode);
        return ApiResponse.ok();
    }
    @Operation(summary = "찜 여부 확인", description = "상세 화면이 하트를 어느 쪽으로 그릴지 정하는 데 쓴다.")
    @GetMapping("/{skuCode}/check")
    public ApiResponse<Boolean> isWishlisted(
            @AuthenticationPrincipal Long userId,
            @PathVariable String skuCode) {
        return ApiResponse.ok(wishlistService.isWishlisted(userId, skuCode));
    }
}
