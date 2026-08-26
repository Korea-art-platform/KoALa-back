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

    @Column(length = 150)
    private String model;

    @Column(name = "sub_model_name", length = 150)
    private String subModelName;

    @Column(name = "model_en", length = 150)
    private String modelEn;

    @Column(name = "sub_model_name_en", length = 150)
    private String subModelNameEn;

    @Column(length = 100)
    private String color;

    @Column(name = "color_en", length = 100)
    private String colorEn;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Lob
    private String description;

    @Column(nullable = false, length = 20)
    private String skuType;

    @Column(nullable = false, length = 50)
    private String mainCategory;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(length = 300)
    private String material;

    @Lob
    private String materialDescription;

    @Column(length = 200)
    private String packagingTitle;

    @Lob
    private String packagingDescription;

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
    private String badges;

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

    /** 무게(g). 아트토이는 1kg 미만이 대부분이라 kg 로는 소수를 넣어야 했다. */
    @Column(name = "weight_g")
    private Integer weightG;

    @Column(nullable = false, length = 20)
    private String status;

    private LocalDateTime publishedAt;
    private LocalDateTime deletedAt;

    @Builder
    public Sku(String skuCode, Artist artist, String name, String model, String subModelName,
               String modelEn, String subModelNameEn, String color, String colorEn, String slug,
               String description, String skuType, String mainCategory, String genre, String material,
               String materialDescription, String packagingTitle, String packagingDescription,
               String currency, BigDecimal listPrice, BigDecimal salePrice,
               Integer editionSize, Integer editionNumber,
               String primaryImageUrl, BigDecimal widthCm, BigDecimal heightCm,
               BigDecimal depthCm, BigDecimal weightKg, Integer weightG,
               String badges) {
        this.skuCode = skuCode;
        this.artist = artist;
        this.name = name;
        this.model = model;
        this.subModelName = subModelName;
        this.modelEn = modelEn;
        this.subModelNameEn = subModelNameEn;
        this.color = color;
        this.colorEn = colorEn;
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
        this.weightG = weightG;
        this.badges = badges;
        this.status = "DRAFT";
    }

    public void update(String name, String slug, String description,
                       String skuType, String mainCategory, String genre, String material,
                       String materialDescription, String packagingTitle, String packagingDescription,
                       BigDecimal listPrice, BigDecimal salePrice, String primaryImageUrl,
                       Integer editionSize, Integer editionNumber,
                       String badges,
                       String model, String subModelName,
                       String modelEn, String subModelNameEn, String color, String colorEn,
                       BigDecimal widthCm, BigDecimal heightCm, BigDecimal depthCm,
                       BigDecimal weightKg, Integer weightG) {
        this.name = name;
        this.model = model;
        this.subModelName = subModelName;
        this.modelEn = modelEn;
        this.subModelNameEn = subModelNameEn;
        this.color = color;
        this.colorEn = colorEn;
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
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.depthCm = depthCm;
        this.weightKg = weightKg;
        this.weightG = weightG;
    }

    public void changePrimaryImage(String primaryImageUrl) {
        this.primaryImageUrl = primaryImageUrl;
    }

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
