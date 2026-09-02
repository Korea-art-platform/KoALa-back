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

    public CartDto.CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return CartDto.CartResponse.from(cart, vatPolicy, vatPolicy.exemptMainCategories());
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
