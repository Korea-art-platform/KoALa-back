package com.koala.koalaback.domain.category.dto;

import com.koala.koalaback.domain.category.entity.SkuCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class SkuCategoryDto {
    @Getter @Setter
    public static class CreateRequest {
        @NotBlank
        @Pattern(regexp = "MAIN|SUB", message = "type 은 MAIN 또는 SUB 여야 합니다.")
        private String type;

        // 코드는 관리자가 입력하지 않는다. 표시 이름으로 서버가 만든다.
        // 상품에 저장되는 값이라 사람이 정할 이유가 없고, 규칙을 설명하기도 어려웠다.
        private String code;

        @NotBlank
        private String name;

        private Integer sortOrder;
    }

    @Getter @Setter
    public static class UpdateRequest {
        private String name;
        private Integer sortOrder;
        private Boolean isActive;
    }

    @Getter
    @Builder
    public static class Response {
        private Long id;
        private String type;
        private String code;
        private String name;
        private Integer sortOrder;
        private Boolean isActive;

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
