package com.koala.koalaback.domain.sku.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sku_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @Column(nullable = false, length = 20)
    private String mediaType;

    @Column(nullable = false, length = 30)
    private String mediaRole;

    @Column(nullable = false, length = 700)
    private String fileUrl;

    @Column(length = 700)
    private String thumbnailUrl;

    @Column(length = 255)
    private String altText;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(precision = 6, scale = 2)
    private BigDecimal angleDegree;

    @Column(nullable = false)
    private Boolean isPrimary;

    @Column(columnDefinition = "JSON")
    private String metaJson;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @Builder
    public SkuMedia(Sku sku, String mediaType, String mediaRole,
                    String fileUrl, String thumbnailUrl, String altText,
                    Integer sortOrder, BigDecimal angleDegree,
                    Boolean isPrimary, String metaJson) {
        this.sku = sku;
        this.mediaType = mediaType;
        this.mediaRole = mediaRole;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.altText = altText;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.angleDegree = angleDegree;
        this.isPrimary = isPrimary != null ? isPrimary : false;
        this.metaJson = metaJson;
    }
}
