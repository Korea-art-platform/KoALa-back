package com.koala.koalaback.api.wishlist;

import com.koala.koalaback.domain.wishlist.dto.WishlistDto;
import com.koala.koalaback.domain.wishlist.service.WishlistService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {
    private final WishlistService wishlistService;

    @GetMapping
    public ApiResponse<PageResponse<WishlistDto.WishlistItemResponse>> getWishlist(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(wishlistService.getWishlist(userId, pageable));
    }

    @PostMapping("/{skuCode}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WishlistDto.WishlistItemResponse> addToWishlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable String skuCode) {
        return ApiResponse.ok(wishlistService.addToWishlist(userId, skuCode));
    }

    @DeleteMapping("/{skuCode}")
    public ApiResponse<Void> removeFromWishlist(
            @AuthenticationPrincipal Long userId,
            @PathVariable String skuCode) {
        wishlistService.removeFromWishlist(userId, skuCode);
        return ApiResponse.ok();
    }
    @GetMapping("/{skuCode}/check")
    public ApiResponse<Boolean> isWishlisted(
            @AuthenticationPrincipal Long userId,
            @PathVariable String skuCode) {
        return ApiResponse.ok(wishlistService.isWishlisted(userId, skuCode));
    }
}
