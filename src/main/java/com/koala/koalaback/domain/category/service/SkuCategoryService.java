package com.koala.koalaback.domain.category.service;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.repository.SkuCategoryRepository;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkuCategoryService {
    private final SkuCategoryRepository categoryRepository;
    private final SkuRepository skuRepository;

    public SkuCategoryDto.GroupedResponse getActiveCategories() {
        List<SkuCategory> all = categoryRepository.findByIsActiveTrueOrderByTypeAscSortOrderAsc();
        return group(all, Map.of());
    }

    public SkuCategoryDto.GroupedResponse getAllCategories() {
        List<SkuCategory> all = categoryRepository.findAllByOrderByTypeAscSortOrderAsc();

        Map<Long, Long> usage = categoryRepository.findUsageCounts().stream()
                .collect(Collectors.toMap(
                        SkuCategoryRepository.CategoryUsage::getCategoryId,
                        SkuCategoryRepository.CategoryUsage::getUsedCount));

        return group(all, usage);
    }

    private SkuCategoryDto.GroupedResponse group(List<SkuCategory> all, Map<Long, Long> usage) {
        Function<SkuCategory, SkuCategoryDto.Response> toResponse =
                c -> SkuCategoryDto.Response.from(c, usage.get(c.getId()));

        return SkuCategoryDto.GroupedResponse.of(
                all.stream().filter(SkuCategory::isMain).map(toResponse).toList(),
                all.stream().filter(SkuCategory::isSub).map(toResponse).toList());
    }

    public Map<String, Set<String>> getActiveCodesByType() {
        return categoryRepository.findByIsActiveTrueOrderByTypeAscSortOrderAsc().stream()
                .collect(Collectors.groupingBy(
                        SkuCategory::getType,
                        Collectors.mapping(SkuCategory::getCode, Collectors.toSet())));
    }

    public void validateCodes(String mainCategory, String subCategory) {
        Map<String, Set<String>> codes = getActiveCodesByType();

        Set<String> mains = codes.getOrDefault(SkuCategory.TYPE_MAIN, Set.of());
        if (mainCategory == null || !mains.contains(mainCategory)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "존재하지 않는 대분류입니다: " + mainCategory);
        }

        if (subCategory != null && !subCategory.isBlank()) {
            Set<String> subs = codes.getOrDefault(SkuCategory.TYPE_SUB, Set.of());
            if (!subs.contains(subCategory)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "존재하지 않는 소분류입니다: " + subCategory);
            }
        }
    }

    @Transactional
    public SkuCategoryDto.Response create(SkuCategoryDto.CreateRequest req) {
        String code = hasText(req.getCode()) ? req.getCode() : generateCode(req.getType(), req.getName());

        if (categoryRepository.existsByTypeAndCode(req.getType(), code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 있는 분류입니다: " + req.getName());
        }

        int sortOrder = req.getSortOrder() != null
                ? req.getSortOrder()
                : categoryRepository.findMaxSortOrder(req.getType()) + 1;

        SkuCategory saved = categoryRepository.save(SkuCategory.builder()
                .type(req.getType())
                .code(code)
                .name(req.getName())
                .sortOrder(sortOrder)
                .build());

        log.info("카테고리 생성: type={}, code={}, name={}",
                saved.getType(), saved.getCode(), saved.getName());
        return SkuCategoryDto.Response.from(saved);
    }

    @Transactional
    public SkuCategoryDto.Response update(Long id, SkuCategoryDto.UpdateRequest req) {
        SkuCategory category = getOrThrow(id);
        category.update(req.getName(), req.getSortOrder(), req.getIsActive());
        return SkuCategoryDto.Response.from(category);
    }

    /**
     * 쓰고 있는 분류는 지울 수 없다.
     *
     * 지우면 그 분류로 등록된 상품이 갈 곳을 잃는다. 목록·검색 필터가 빈 값을
     * 만나고, 홈의 소분류 섹션도 비어 버린다. 상품을 먼저 옮기게 해야 한다.
     */
    @Transactional
    public void deactivate(Long id) {
        SkuCategory category = getOrThrow(id);

        long inUse = "MAIN".equals(category.getType())
                ? skuRepository.countByMainCategoryAndDeletedAtIsNull(category.getCode())
                : skuRepository.countByGenreAndDeletedAtIsNull(category.getCode());

        if (inUse > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이 분류로 등록된 상품이 " + inUse + "개 있어 삭제할 수 없습니다. "
                    + "해당 상품의 분류를 먼저 바꿔 주세요.");
        }

        category.deactivate();
        log.info("카테고리 비활성화: type={}, code={}", category.getType(), category.getCode());
    }

    private boolean hasText(String v) { return v != null && !v.isBlank(); }

    /**
     * 표시 이름으로 코드를 만든다. 한글 이름은 영문 코드로 옮길 수 없으므로
     * 타입 약자에 일련번호를 붙인다. 코드는 화면에 보이지 않는 내부 값이다.
     */
    private String generateCode(String type, String name) {
        String ascii = name == null ? "" : name.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        ascii = ascii.replaceAll("(^_+|_+$)", "");

        if (!ascii.isBlank() && !categoryRepository.existsByTypeAndCode(type, ascii)) {
            return ascii.length() > 50 ? ascii.substring(0, 50) : ascii;
        }

        String prefix = "MAIN".equals(type) ? "MAIN" : "SUB";
        for (int i = 1; i < 1000; i++) {
            String candidate = prefix + "_" + i;
            if (!categoryRepository.existsByTypeAndCode(type, candidate)) return candidate;
        }
        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "코드를 만들 수 없습니다.");
    }

    private SkuCategory getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카테고리를 찾을 수 없습니다."));
    }
}
