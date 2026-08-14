package com.koala.koalaback.domain.artist.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "artists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String artistCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String artistNote;

    @Column(length = 700)
    private String profileImageUrl;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "featured_sku_id", columnDefinition = "BIGINT UNSIGNED")
    private Long featuredSkuId;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ArtistMedia> mediaList = new ArrayList<>();

    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("category ASC, sortOrder ASC")
    private List<ArtistCareer> careerList = new ArrayList<>();

    @Builder
    public Artist(String artistCode, String name, String slug,
                  String description, String artistNote, String profileImageUrl) {
        this.artistCode      = artistCode;
        this.name            = name;
        this.slug            = slug;
        this.description     = description;
        this.artistNote      = artistNote;
        this.profileImageUrl = profileImageUrl;
        this.isActive        = true;
        this.commissionRate  = DEFAULT_COMMISSION_RATE;
    }

    public static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.2000");

    public void changeCommissionRate(BigDecimal rate) {
        if (rate == null
                || rate.compareTo(BigDecimal.ZERO) < 0
                || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("수수료율은 0 이상 1 미만이어야 합니다.");
        }
        this.commissionRate = rate;
    }

    public void update(String name, String slug,
                       String description, String artistNote, String profileImageUrl) {
        this.name            = name;
        this.slug            = slug;
        this.description     = description;
        this.artistNote      = artistNote;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateProfileImage(String url) {
        this.profileImageUrl = url;
    }

    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    public void setFeaturedSku(Long skuId)  { this.featuredSkuId = skuId; }
    public void clearFeaturedSku()           { this.featuredSkuId = null; }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }
}
