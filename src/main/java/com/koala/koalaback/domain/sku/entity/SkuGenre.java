package com.koala.koalaback.domain.sku.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/**
 * 상품 장르 — 허용값의 단일 출처.
 *
 * <p>DB 컬럼은 varchar 이고 CHECK 제약이 없어서, 오타가 그대로 저장되면
 * 장르 필터에서 영영 안 잡히는 상품이 된다. 등록 경로에서 이 목록으로 막는다.
 *
 * <p>엔티티는 여전히 {@code String genre} 를 쓴다. @Enumerated 매핑으로 바꾸면
 * 기존 데이터와 마이그레이션까지 건드려야 해서, 지금은 검증·목록 제공용으로만 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum SkuGenre {

    ART_TOY("아트 토이"),
    SCULPTURE("조각"),
    PAINTING("페인팅"),
    PRINT("판화 / 프린트"),
    PHOTOGRAPH("사진"),
    INSTALLATION("설치 미술"),
    TEXTILE("섬유 / 직물"),
    OTHER("기타");

    /** 관리자 화면에 보여줄 한글 이름 */
    private final String label;

    /** 값을 비워 올린 경우 적용할 기본 장르 (Sku 엔티티 기본값과 동일하게 유지) */
    public static final SkuGenre DEFAULT = ART_TOY;

    public static boolean isValid(String code) {
        return code != null && Arrays.stream(values())
                .anyMatch(g -> g.name().equals(code));
    }

    /** 오류 메시지·안내 문구용 */
    public static List<String> codes() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
