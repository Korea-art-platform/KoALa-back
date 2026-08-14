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

    @GetMapping
    public ApiResponse<SkuCategoryDto.GroupedResponse> getCategories() {
        return ApiResponse.ok(categoryService.getActiveCategories());
    }
}
