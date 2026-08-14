package com.koala.koalaback.domain.cart.repository;

import com.koala.koalaback.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartIdAndSkuId(Long cartId, Long skuId);
}
