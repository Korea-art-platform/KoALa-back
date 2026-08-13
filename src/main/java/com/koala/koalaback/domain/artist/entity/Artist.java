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

    /**
     * 플랫폼 수수료율 — 0.2000 = 20%.
     *
     * <p>작가마다 계약이 달라 컬럼으로 둔다. 정산을 확정할 때 이 값을 스냅샷으로
     * 함께 저장하므로, 나중에 요율을 바꿔도 이미 지급한 달의 금액은 바뀌지 않는다.
     */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    /** 대표 작품 SKU ID (nullable) — ON DELETE SET NULL */
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

    /** 계약이 정해지기 전 기본값 — 어드민에서 작가별로 조정한다 */
    public static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.2000");

    /**
     * 수수료율 변경.
     *
     * <p>0 이상 1 미만만 허용한다. 1 이상이면 작가에게 갈 돈이 0 이하가 되고,
     * 음수면 팔수록 플랫폼이 손해를 본다. 둘 다 오타로 들어올 수 있는 값이다.
     */
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