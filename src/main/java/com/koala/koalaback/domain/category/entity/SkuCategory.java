package com.koala.koalaback.domain.category.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sku_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuCategory extends BaseTimeEntity {
    public static final String TYPE_MAIN = "MAIN";
    public static final String TYPE_SUB = "SUB";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, updatable = false)
    private String type;

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean isActive;

    /**
     * 면세 분류인가.
     *
     * 미술품 원작에는 부가세를 붙이지 않고, 한정판·오픈에디션에는 붙인다.
     * 어느 쪽인지는 세무 판단이라 코드에 박지 않고 분류에 달아 둔다.
     */
    @Column(nullable = false)
    private Boolean taxExempt;

    @Builder
    public SkuCategory(String type, String code, String name, Integer sortOrder, Boolean taxExempt) {
        this.type = type;
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = true;
        this.taxExempt = taxExempt != null ? taxExempt : false;
    }

    public void update(String name, Integer sortOrder, Boolean isActive, Boolean taxExempt) {
        if (name != null && !name.isBlank()) this.name = name;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (isActive != null) this.isActive = isActive;
        if (taxExempt != null) this.taxExempt = taxExempt;
    }

    public boolean isTaxExempt() { return Boolean.TRUE.equals(this.taxExempt); }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isMain() { return TYPE_MAIN.equals(this.type); }
    public boolean isSub()  { return TYPE_SUB.equals(this.type); }
}
