package com.koala.koalaback.domain.review.repository;

import com.koala.koalaback.domain.review.entity.SkuReviewMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkuReviewMediaRepository extends JpaRepository<SkuReviewMedia, Long> {
    List<SkuReviewMedia> findByReviewIdOrderBySortOrderAsc(Long reviewId);

    void deleteByReviewId(Long reviewId);
}
