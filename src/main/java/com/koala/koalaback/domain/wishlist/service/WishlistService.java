package com.koala.koalaback.domain.wishlist.service;

import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.domain.wishlist.dto.WishlistDto;
import com.koala.koalaback.domain.wishlist.entity.WishlistItem;
import com.koala.koalaback.domain.wishlist.repository.WishlistItemRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {
    private final WishlistItemRepository wishlistItemRepository;
    private final UserService userService;
    private final SkuService skuService;

    public PageResponse<WishlistDto.WishlistItemResponse> getWishlist(
            Long userId, Pageable pageable) {
        return PageResponse.of(
                wishlistItemRepository
                        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(WishlistDto.WishlistItemResponse::from)
        );
    }

    @Transactional
    public WishlistDto.WishlistItemResponse addToWishlist(Long userId, String skuCode) {
        Sku sku = skuService.getSkuEntityByCode(skuCode);

        if (wishlistItemRepository.existsByUserIdAndSkuId(userId, sku.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        WishlistItem item = WishlistItem.builder()
                .user(userService.getUserById(userId))
                .sku(sku)
                .build();

        return WishlistDto.WishlistItemResponse.from(wishlistItemRepository.save(item));
    }

    @Transactional
    public void removeFromWishlist(Long userId, String skuCode) {
        Sku sku = skuService.getSkuEntityByCode(skuCode);
        wishlistItemRepository.deleteByUserIdAndSkuId(userId, sku.getId());
    }

    public boolean isWishlisted(Long userId, String skuCode) {
        Sku sku = skuService.getSkuEntityByCode(skuCode);
        return wishlistItemRepository.existsByUserIdAndSkuId(userId, sku.getId());
    }
}
