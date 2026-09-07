package com.koala.koalaback.api;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "분류", description = "대분류·소분류 목록")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final SkuCategoryService categoryService;

    @Operation(summary = "분류 목록", description = """
            대분류와 소분류를 묶어 한 번에 내려준다. 분류 이름은 코드가 아니라 DB 값이
            기준이다 — 화면마다 이름을 따로 적어 두면 어드민에서 바꿔도 한쪽만 바뀐다.
            """)
    @GetMapping
    public ApiResponse<SkuCategoryDto.GroupedResponse> getCategories() {
        return ApiResponse.ok(categoryService.getActiveCategories());
    }
}
