package com.koala.koalaback.domain.cart.service;

import com.koala.koalaback.domain.cart.dto.CartDto;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.domain.cart.entity.Cart;
import com.koala.koalaback.domain.cart.entity.CartItem;
import com.koala.koalaback.domain.cart.repository.CartItemRepository;
import com.koala.koalaback.domain.cart.repository.CartRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {
    private final CartRepository cartRepository;
    private final VatPolicy vatPolicy;
    private final CartItemRepository cartItemRepository;
    private final UserService userService;
    private final SkuService skuService;
    private final StockService stockService;

    /**
     * 담아 둔 것을 보여준다.
     *
     * 없으면 만들지 않고 빈 장바구니를 돌려준다. 읽기만 하는 자리에서 새로
     * 만들려다 "읽기 전용 트랜잭션" 오류로 500 이 났다 — 가입한 적만 있고
     * 아직 아무것도 안 담은 사람은 장바구니 화면 자체를 못 열었다.
     *
     * 만드는 일은 실제로 담을 때(addItem) 하면 된다. 비어 있는 장바구니를
     * 미리 만들어 둘 이유도 없다.
     */
    public CartDto.CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> CartDto.CartResponse.from(cart, vatPolicy, vatPolicy.exemptMainCategories()))
                .orElseGet(CartDto.CartResponse::empty);
    }

    @Transactional
    public CartDto.CartResponse addItem(Long userId, CartDto.AddItemRequest req) {
        Cart cart = getOrCreateCart(userId);
        Sku sku = skuService.getSkuEntityByCode(req.getSkuCode());

        if (!sku.isAvailable()) {
            throw new BusinessException(ErrorCode.SKU_NOT_ACTIVE);
        }
        if (stockService.getStock(sku.getId()) < req.getQuantity()) {
            throw new BusinessException(ErrorCode.SKU_OUT_OF_STOCK);
        }

        cartItemRepository.findByCartIdAndSkuId(cart.getId(), sku.getId())
                .ifPresentOrElse(
                        existing -> existing.updateQuantity(
                                existing.getQuantity() + req.getQuantity()),
                        () -> {
                            CartItem item = CartItem.builder()
                                    .cart(cart)
                                    .sku(sku)
                                    .quantity(req.getQuantity())
                                    .unitPrice(sku.getEffectivePrice())
                                    .build();
                            cartItemRepository.save(item);
                            cart.addItem(item);
                        }
                );

        return CartDto.CartResponse.from(cart, vatPolicy, vatPolicy.exemptMainCategories());
    }

    @Transactional
    public CartDto.CartResponse updateItem(Long userId, Long itemId,
                                           CartDto.UpdateItemRequest req) {
        Cart cart = getCartByUserId(userId);
        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (stockService.getStock(item.getSku().getId()) < req.getQuantity()) {
            throw new BusinessException(ErrorCode.SKU_OUT_OF_STOCK);
        }

        item.updateQuantity(req.getQuantity());
        return CartDto.CartResponse.from(cart, vatPolicy, vatPolicy.exemptMainCategories());
    }

    @Transactional
    public CartDto.CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = getCartByUserId(userId);
        CartItem item = cartItemRepository.findById(itemId)
                .filter(i -> i.getCart().getId().equals(cart.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        cart.removeItem(item);
        cartItemRepository.delete(item);
        return CartDto.CartResponse.from(cart, vatPolicy, vatPolicy.exemptMainCategories());
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().clear();
    }

    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(
                        Cart.builder()
                                .user(userService.getUserById(userId))
                                .build()
                ));
    }

    private Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));
    }
}
