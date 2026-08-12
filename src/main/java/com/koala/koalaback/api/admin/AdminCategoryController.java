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

    /** 비활성 포함 전체 — 각 항목에 usedCount(사용 중인 상품 수) 포함 */
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

    /** 이름·순서·활성여부만 수정 (code·type 은 상품이 참조 중이라 불변) */
    @PatchMapping("/{id}")
    public ApiResponse<SkuCategoryDto.Response> updateCategory(
            @PathVariable Long id,
            @RequestBody SkuCategoryDto.UpdateRequest req) {
        return ApiResponse.ok(categoryService.update(id, req));
    }

    /** 실제 삭제가 아니라 비활성화 — 기존 상품의 카테고리 이력을 지우지 않는다 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ApiResponse.ok();
    }
}
