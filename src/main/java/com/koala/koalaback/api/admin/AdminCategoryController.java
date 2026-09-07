package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.global.response.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 분류", description = "대분류·소분류 관리")
@RestController
@RequestMapping("/admin/api/v1/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {
    private final SkuCategoryService categoryService;

    @Operation(summary = "분류 목록 (어드민)", description = "비활성 분류까지 보인다.")
    @GetMapping
    public ApiResponse<SkuCategoryDto.GroupedResponse> getCategories() {
        return ApiResponse.ok(categoryService.getAllCategories());
    }

    @Operation(summary = "분류 등록", description = """
            코드는 입력하지 않는다. 표시 이름으로 서버가 만든다 — 한글 이름은 영문 코드로
            옮길 수 없어 타입 약자에 일련번호를 붙인다. 코드는 화면에 보이지 않는 내부
            값이다.

            같은 이름이나 같은 코드가 이미 있으면 DUPLICATE_RESOURCE.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkuCategoryDto.Response> createCategory(
            @Valid @RequestBody SkuCategoryDto.CreateRequest req) {
        return ApiResponse.ok(categoryService.create(req));
    }

    @Operation(summary = "분류 수정", description = "표시 이름을 바꾼다. 이름이 겹치면 거절한다.")
    @PatchMapping("/{id}")
    public ApiResponse<SkuCategoryDto.Response> updateCategory(
            @PathVariable Long id,
            @RequestBody SkuCategoryDto.UpdateRequest req) {
        return ApiResponse.ok(categoryService.update(id, req));
    }

    @Operation(summary = "분류 비활성화", description = """
            지우는 것이 아니라 내린다. 그리고 쓰고 있는 분류는 내릴 수 없다 — 그 분류로
            등록된 상품이 갈 곳을 잃는다. 목록·검색 필터가 빈 값을 만나고 홈의 소분류
            섹션도 비어 버린다. 상품을 먼저 옮겨야 한다.
            """)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivate(id);
        return ApiResponse.ok();
    }
}
