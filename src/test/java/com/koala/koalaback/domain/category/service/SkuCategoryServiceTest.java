package com.koala.koalaback.domain.category.service;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.repository.SkuCategoryRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 카테고리 CRUD 규칙 검증.
 *
 * <p>실제 MySQL 을 쓴다. (type, code) UNIQUE 와 code 불변이 이 도메인의 핵심 제약인데
 * 둘 다 DB/영속성 계층에서 걸리는 것이라 목으로는 확인되지 않는다.
 */
@DisplayName("상품 카테고리")
class SkuCategoryServiceTest extends IntegrationTestSupport {

    private static final String PREFIX = "CATTEST_";

    @Autowired private SkuCategoryService categoryService;
    @Autowired private SkuCategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM sku_categories WHERE code LIKE ?", PREFIX + "%");
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("같은 type 안에서 code 가 겹치면 거절한다")
        void duplicateCodeRejected() {
            categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "CERAMIC", "도자"));

            assertThatThrownBy(() ->
                    categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "CERAMIC", "도예")))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("type 이 다르면 같은 code 를 쓸 수 있다 — 대분류·소분류는 독립 축이다")
        void sameCodeAcrossTypesAllowed() {
            categoryService.create(request(SkuCategory.TYPE_MAIN, PREFIX + "SPECIAL", "특별전"));
            categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "SPECIAL", "특별 장르"));

            assertThat(categoryRepository.findByTypeAndCode(SkuCategory.TYPE_MAIN, PREFIX + "SPECIAL"))
                    .isPresent();
            assertThat(categoryRepository.findByTypeAndCode(SkuCategory.TYPE_SUB, PREFIX + "SPECIAL"))
                    .isPresent();
        }

        @Test
        @DisplayName("sortOrder 를 안 주면 그 type 의 맨 뒤로 붙는다")
        void sortOrderDefaultsToLast() {
            int before = categoryRepository.findMaxSortOrder(SkuCategory.TYPE_SUB);

            SkuCategoryDto.Response created =
                    categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "GLASS", "유리"));

            assertThat(created.getSortOrder()).isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("수정·비활성화")
    class Modify {

        @Test
        @DisplayName("이름과 순서는 바꿔도 code 는 그대로다 — 상품이 code 를 문자열로 들고 있다")
        void codeIsImmutable() {
            SkuCategoryDto.Response created =
                    categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "WOOD", "목공"));

            SkuCategoryDto.UpdateRequest req = new SkuCategoryDto.UpdateRequest();
            req.setName("목공예");
            req.setSortOrder(99);
            SkuCategoryDto.Response updated = categoryService.update(created.getId(), req);

            assertThat(updated.getName()).isEqualTo("목공예");
            assertThat(updated.getSortOrder()).isEqualTo(99);
            assertThat(updated.getCode()).isEqualTo(PREFIX + "WOOD");
        }

        @Test
        @DisplayName("비활성화해도 행은 남는다 — 지난 주문의 카테고리 이력이 사라지면 안 된다")
        void deactivateKeepsRow() {
            SkuCategoryDto.Response created =
                    categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "PAPER", "종이"));

            categoryService.deactivate(created.getId());

            assertThat(categoryRepository.findById(created.getId()))
                    .get().extracting(SkuCategory::getIsActive).isEqualTo(false);
        }

        @Test
        @DisplayName("비활성 카테고리는 공개 목록·검증 목록에서 빠진다")
        void inactiveExcludedFromActiveViews() {
            SkuCategoryDto.Response created =
                    categoryService.create(request(SkuCategory.TYPE_SUB, PREFIX + "NEON", "네온"));
            categoryService.deactivate(created.getId());

            Set<String> subCodes = categoryService.getActiveCodesByType()
                    .getOrDefault(SkuCategory.TYPE_SUB, Set.of());
            assertThat(subCodes).doesNotContain(PREFIX + "NEON");

            assertThat(categoryService.getActiveCategories().getSub())
                    .extracting(SkuCategoryDto.Response::getCode)
                    .doesNotContain(PREFIX + "NEON");

            // 어드민 목록에는 계속 보여야 다시 켤 수 있다
            assertThat(categoryService.getAllCategories().getSub())
                    .extracting(SkuCategoryDto.Response::getCode)
                    .contains(PREFIX + "NEON");
        }
    }

    private SkuCategoryDto.CreateRequest request(String type, String code, String name) {
        SkuCategoryDto.CreateRequest req = new SkuCategoryDto.CreateRequest();
        req.setType(type);
        req.setCode(code);
        req.setName(name);
        return req;
    }
}
