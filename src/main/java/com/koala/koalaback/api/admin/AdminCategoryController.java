package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/v1/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {
    private final SkuCategoryService categoryService;

    @GetMapping
    public ApiResponse<SkuCategoryDto.GroupedResponse> getCategories() {
        return ApiResponse.ok(categoryService.getAllCategories());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkuCategoryDto.Response> createCategory(
            @Valid @RequestBody SkuCategoryDto.CreateRequest req) {
        return ApiResponse.ok(categoryService.create(req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<SkuCategoryDto.Response> updateCategory(
            @PathVariable Long id,
            @RequestBody SkuCategoryDto.UpdateRequest req) {
        return ApiResponse.ok(categoryService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ApiResponse.ok();
    }
}
