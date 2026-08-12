package com.koala.koalaback.domain.category.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 카테고리 — 대분류(MAIN)와 소분류(SUB)를 한 테이블에서 관리한다.
 *
 * <p>계층이 아니라 <b>독립 2축</b>이다.
 * 대분류는 판매 형태(한정판/일반), 소분류는 장르(조각/아트토이/…)라서 서로 종속되지 않는다.
 * 계층으로 두면 '조각'을 한정판 밑과 일반 밑에 두 번 만들어야 한다.
 *
 * <p>{@code code} 는 {@code skus.main_category} / {@code skus.genre} 에 문자열로 저장된다.
 * 그래서 <b>생성 후 code 와 type 은 바꿀 수 없다</b> — 바꾸면 기존 상품이 미아가 된다.
 * 이름·순서·활성여부만 수정할 수 있다.
 */
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

    /** 상품에 저장되는 값 — 변경 불가 */
    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    /** 화면 표시명 */
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean isActive;

    @Builder
    public SkuCategory(String type, String code, String name, Integer sortOrder) {
        this.type = type;
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = true;
    }

    /** code·type 은 그대로 두고 표시 정보만 바꾼다 */
    public void update(String name, Integer sortOrder, Boolean isActive) {
        if (name != null && !name.isBlank()) this.name = name;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (isActive != null) this.isActive = isActive;
    }

    /** 삭제 대신 숨김 — 지난 주문 상품이 어느 카테고리였는지 이력이 남아야 한다 */
    public void deactivate() {
        this.isActive = false;
    }

    public boolean isMain() { return TYPE_MAIN.equals(this.type); }
    public boolean isSub()  { return TYPE_SUB.equals(this.type); }
}
