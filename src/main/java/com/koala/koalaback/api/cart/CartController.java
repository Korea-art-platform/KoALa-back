package com.koala.koalaback.api.cart;

import com.koala.koalaback.domain.cart.dto.CartDto;
import com.koala.koalaback.domain.cart.service.CartService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartDto.CartResponse> getCart(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ApiResponse<CartDto.CartResponse> addItem(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CartDto.AddItemRequest req) {
        return ApiResponse.ok(cartService.addItem(userId, req));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartDto.CartResponse> updateItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartDto.UpdateItemRequest req) {
        return ApiResponse.ok(cartService.updateItem(userId, itemId, req));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartDto.CartResponse> removeItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId) {
        return ApiResponse.ok(cartService.removeItem(userId, itemId));
    }

    @DeleteMapping
    public ApiResponse<Void> clearCart(@AuthenticationPrincipal Long userId) {
        cartService.clearCart(userId);
        return ApiResponse.ok();
    }
}
