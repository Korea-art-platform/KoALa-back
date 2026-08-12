package com.koala.koalaback.domain.category.dto;

import com.koala.koalaback.domain.category.entity.SkuCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class SkuCategoryDto {

    // ── 요청 ──────────────────────────────────────────────

    @Getter @Setter
    public static class CreateRequest {
        /** MAIN(대분류) / SUB(소분류) */
        @NotBlank
        @Pattern(regexp = "MAIN|SUB", message = "type 은 MAIN 또는 SUB 여야 합니다.")
        private String type;

        /** 상품에 저장되는 값이라 한글·공백을 쓸 수 없다 */
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "코드는 영문 대문자·숫자·언더바만 사용할 수 있습니다.")
        private String code;

        @NotBlank
        private String name;

        /** 생략하면 해당 type 의 마지막 순서 다음으로 */
        private Integer sortOrder;
    }

    /** 부분 수정 — code·type 은 바꿀 수 없다 (상품이 참조 중) */
    @Getter @Setter
    public static class UpdateRequest {
        private String name;
        private Integer sortOrder;
        private Boolean isActive;
    }

    // ── 응답 ──────────────────────────────────────────────

    @Getter
    @Builder
    public static class Response {
        private Long id;
        private String type;
        private String code;
        private String name;
        private Integer sortOrder;
        private Boolean isActive;
        /** 이 카테고리를 쓰는 상품 수 — 어드민에서만 채운다 */
        private Long usedCount;

        public static Response from(SkuCategory c) {
            return from(c, null);
        }

        public static Response from(SkuCategory c, Long usedCount) {
            return Response.builder()
                    .id(c.getId())
                    .type(c.getType())
                    .code(c.getCode())
                    .name(c.getName())
                    .sortOrder(c.getSortOrder())
                    .isActive(c.getIsActive())
                    .usedCount(usedCount)
                    .build();
        }
    }

    /** 대분류·소분류를 나눠서 내려준다 — 화면에서 드롭다운 두 개로 쓰기 때문 */
    @Getter
    @Builder
    public static class GroupedResponse {
        private List<Response> main;
        private List<Response> sub;

        public static GroupedResponse of(List<Response> main, List<Response> sub) {
            return GroupedResponse.builder().main(main).sub(sub).build();
        }
    }
}
