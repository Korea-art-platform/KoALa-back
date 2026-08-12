package com.koala.koalaback.domain.sku.entity;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "skus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sku extends BaseTimeEntity {

    /**
     * 기본 제공 대분류 코드. 대분류는 관리자가 추가할 수 있지만
     * 이 두 개는 "한정판이면 에디션 정보를 받는다" 규칙 때문에 코드에서도 참조한다.
     */
    public static final String MAIN_LIMITED = "LIMITED";
    public static final String MAIN_NORMAL = "NORMAL";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String skuCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Lob
    private String description;

    @Column(nullable = false, length = 20)
    private String skuType;     // ARTWORK, GOODS

    /** 대분류 코드 — sku_categories(type='MAIN').code. 예: LIMITED, NORMAL */
    @Column(nullable = false, length = 50)
    private String mainCategory;

    /** 소분류 코드 — sku_categories(type='SUB').code. 예: SCULPTURE, ART_TOY */
    @Column(nullable = false, length = 50)
    private String genre;

    @Column(length = 300)
    private String material;          // 재질/소재 (예: 레진, 아크릴, 캔버스에 유화)

    @Lob
    private String materialDescription; // 재질/소재 상세 설명 (상세 페이지 표시용)

    @Column(length = 200)
    private String packagingTitle;       // 포장 섹션 제목

    @Lob
    private String packagingDescription; // 포장 섹션 설명

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal listPrice;

    @Column(precision = 13, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private Boolean isLimitedEdition;

    private Integer editionSize;
    private Integer editionNumber;

    @Column(columnDefinition = "JSON")
    private String badges;              // e.g. [{"text":"진품 보증","type":"blue"}]

    @Column(length = 700)
    private String primaryImageUrl;

    @Column(length = 700)
    private String arAssetUrl;

    @Column(length = 700)
    private String arPreviewImageUrl;

    @Column(columnDefinition = "JSON")
    private String spinePicturesJson;

    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private BigDecimal depthCm;
    private BigDecimal weightKg;

    @Column(nullable = false, length = 20)
    private String status;      // DRAFT, ACTIVE, OUT_OF_STOCK, DISCONTINUED

    private LocalDateTime publishedAt;
    private LocalDateTime deletedAt;

    @Builder
    public Sku(String skuCode, Artist artist, String name, String slug,
               String description, String skuType, String mainCategory, String genre, String material,
               String materialDescription, String packagingTitle, String packagingDescription,
               String currency, BigDecimal listPrice, BigDecimal salePrice,
               Integer editionSize, Integer editionNumber,
               String primaryImageUrl, BigDecimal widthCm, BigDecimal heightCm,
               BigDecimal depthCm, BigDecimal weightKg,
               String badges) {
        this.skuCode = skuCode;
        this.artist = artist;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.skuType = skuType != null ? skuType : "ARTWORK";
        this.mainCategory = mainCategory != null && !mainCategory.isBlank()
                ? mainCategory
                : MAIN_NORMAL;
        this.genre = genre != null ? genre : "ART_TOY";
        this.material = material;
        this.materialDescription = materialDescription;
        this.packagingTitle = packagingTitle;
        this.packagingDescription = packagingDescription;
        this.currency = currency != null ? currency : "KRW";
        this.listPrice = listPrice;
        this.salePrice = salePrice;
        this.isLimitedEdition = MAIN_LIMITED.equals(this.mainCategory);
        this.editionSize = editionSize;
        this.editionNumber = editionNumber;
        this.primaryImageUrl = primaryImageUrl;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.depthCm = depthCm;
        this.weightKg = weightKg;
        this.badges = badges;
        this.status = "DRAFT";
    }

    public void update(String name, String slug, String description,
                       String skuType, String mainCategory, String genre, String material,
                       String materialDescription, String packagingTitle, String packagingDescription,
                       BigDecimal listPrice, BigDecimal salePrice, String primaryImageUrl,
                       Integer editionSize, Integer editionNumber,
                       String badges) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        if (skuType != null && !skuType.isBlank()) this.skuType = skuType;
        if (mainCategory != null && !mainCategory.isBlank()) changeMainCategory(mainCategory);
        if (genre    != null && !genre.isBlank())    this.genre = genre;
        this.material = material;
        this.materialDescription = materialDescription;
        this.packagingTitle = packagingTitle;
        this.packagingDescription = packagingDescription;
        this.listPrice = listPrice;
        this.salePrice = salePrice;
        this.primaryImageUrl = primaryImageUrl;
        this.editionSize = editionSize;
        this.editionNumber = editionNumber;
        this.badges = badges;
    }

    /**
     * 대분류 변경. {@code isLimitedEdition} 은 여기서만 따라 바뀐다.
     *
     * <p>한정판 여부는 이제 대분류에서 파생되는 값이다. 따로 받으면
     * "대분류=NORMAL 인데 isLimitedEdition=true" 같은 모순이 생긴다.
     * 컬럼을 남겨두는 건 기존 화면·쿼리가 이미 쓰고 있어서다.
     */
    public void changeMainCategory(String mainCategory) {
        this.mainCategory = mainCategory;
        this.isLimitedEdition = MAIN_LIMITED.equals(mainCategory);
    }

    public void publish() {
        this.status = "ACTIVE";
        this.publishedAt = LocalDateTime.now();
    }

    public void discontinue() {
        this.status = "DISCONTINUED";
    }

    public void markOutOfStock() {
        this.status = "OUT_OF_STOCK";
    }

    public void markActive() {
        this.status = "ACTIVE";
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.status = "DISCONTINUED";
    }

    public boolean isAvailable() {
        return "ACTIVE".equals(this.status) && this.deletedAt == null;
    }

    public BigDecimal getEffectivePrice() {
        return salePrice != null ? salePrice : listPrice;
    }
}