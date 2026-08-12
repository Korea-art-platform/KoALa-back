package com.koala.koalaback.api;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final SkuCategoryService categoryService;

    /**
     * 활성 카테고리 목록 — 대분류·소분류를 나눠서 내려준다.
     *
     * <p>상품 응답에는 카테고리 이름을 넣지 않는다. 화면에서 이 목록을 한 번 받아
     * {@code code → name} 으로 매핑한다. 홈·상품폼 모두 목록이 필요해 중복 전송을 피한다.
     */
    @GetMapping
    public ApiResponse<SkuCategoryDto.GroupedResponse> getCategories() {
        return ApiResponse.ok(categoryService.getActiveCategories());
    }
}
