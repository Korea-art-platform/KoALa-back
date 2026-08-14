package com.koala.koalaback.domain.sku.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sku_review_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuReviewStats {
    @Id
    @Column(name = "sku_id")
    private Long skuId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "sku_id")
    private Sku sku;

    @Column(nullable = false)
    private Integer reviewCount;

    @Column(nullable = false)
    private Long ratingSum;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating;

    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    @Builder
    public SkuReviewStats(Sku sku) {
        this.sku = sku;
        this.reviewCount = 0;
        this.ratingSum = 0L;
        this.avgRating = BigDecimal.ZERO;
    }

    public void addReview(int rating) {
        this.reviewCount++;
        this.ratingSum += rating;
        recalcAvg();
    }

    public void removeReview(int rating) {
        if (this.reviewCount > 0) {
            this.reviewCount--;
            this.ratingSum -= rating;
            recalcAvg();
        }
    }

    private void recalcAvg() {
        if (this.reviewCount == 0) {
            this.avgRating = BigDecimal.ZERO;
        } else {
            this.avgRating = BigDecimal.valueOf(this.ratingSum)
                    .divide(BigDecimal.valueOf(this.reviewCount), 2, java.math.RoundingMode.HALF_UP);
        }
    }
}
