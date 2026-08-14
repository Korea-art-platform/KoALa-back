package com.koala.koalaback.domain.wishlist.repository;

import com.koala.koalaback.domain.wishlist.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    boolean existsByUserIdAndSkuId(Long userId, Long skuId);

    Optional<WishlistItem> findByUserIdAndSkuId(Long userId, Long skuId);

    Page<WishlistItem> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    void deleteByUserIdAndSkuId(Long userId, Long skuId);
}
