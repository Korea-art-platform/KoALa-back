package com.koala.koalaback.api.cart;

import com.koala.koalaback.domain.cart.dto.CartDto;
import com.koala.koalaback.domain.cart.service.CartService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "장바구니", description = "담기·수량 변경·비우기. 응답은 언제나 장바구니 전체다")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @Operation(summary = "장바구니 조회", description = """
            장바구니가 없으면 만들지 않고 빈 장바구니를 돌려준다. 읽기만 하는 자리에서
            새로 만들려다 '읽기 전용 트랜잭션' 오류로 500 이 났다 — 가입만 하고 아직
            아무것도 안 담은 사람이 장바구니 화면 자체를 열지 못했다.

            만드는 일은 실제로 담을 때 한다. 비어 있는 장바구니를 미리 만들어 둘 이유가
            없다.

            금액은 부가세를 더해 내려간다. 화면에 찍히는 숫자와 결제 금액이 같아야 한다.
            원작처럼 면세로 표시된 분류에는 붙지 않는다.
            """)
    @GetMapping
    public ApiResponse<CartDto.CartResponse> getCart(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(cartService.getCart(userId));
    }

    @Operation(summary = "장바구니에 담기", description = """
            판매 중인 상품만 담을 수 있고, 재고보다 많이 담을 수 없다.

            이미 담긴 상품이면 새 줄을 만들지 않고 수량을 더한다. 같은 작품이 두 줄로
            나뉘면 수량을 고칠 때 어느 줄인지 헷갈린다.

            담는 시점의 판매가를 함께 저장한다. 다만 결제 금액은 주문할 때 DB 에서
            다시 계산하므로, 담아 둔 사이 가격이 바뀌어도 옛 가격으로 팔리지 않는다.
            """)
    @PostMapping("/items")
    public ApiResponse<CartDto.CartResponse> addItem(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CartDto.AddItemRequest req) {
        return ApiResponse.ok(cartService.addItem(userId, req));
    }

    @Operation(summary = "수량 변경", description = """
            더하는 것이 아니라 그 수량으로 맞춘다. 재고보다 많으면 거절한다.

            항목 id 가 내 장바구니의 것인지 확인한다. 남의 항목 id 를 알아도 바뀌지 않는다.
            """)
    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartDto.CartResponse> updateItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId,
            @Valid @RequestBody CartDto.UpdateItemRequest req) {
        return ApiResponse.ok(cartService.updateItem(userId, itemId, req));
    }

    @Operation(summary = "항목 삭제", description = "내 장바구니의 항목인지 확인한 뒤 지운다.")
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartDto.CartResponse> removeItem(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long itemId) {
        return ApiResponse.ok(cartService.removeItem(userId, itemId));
    }

    @Operation(summary = "장바구니 비우기", description = """
            담긴 것을 전부 지운다. 장바구니 자체는 남는다.
            """)
    @DeleteMapping
    public ApiResponse<Void> clearCart(@AuthenticationPrincipal Long userId) {
        cartService.clearCart(userId);
        return ApiResponse.ok();
    }
}
