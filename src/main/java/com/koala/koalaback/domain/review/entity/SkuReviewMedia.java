package com.koala.koalaback.domain.review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sku_review_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuReviewMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private SkuReview review;

    @Column(nullable = false, length = 20)
    private String mediaType;

    @Column(nullable = false, length = 700)
    private String fileUrl;

    @Column(length = 700)
    private String thumbnailUrl;

    @Column(nullable = false)
    private Integer sortOrder;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @Builder
    public SkuReviewMedia(SkuReview review, String mediaType,
                          String fileUrl, String thumbnailUrl, Integer sortOrder) {
        this.review = review;
        this.mediaType = mediaType;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }
}
