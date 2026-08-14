package com.koala.koalaback.domain.category.service;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.repository.SkuCategoryRepository;
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
        if (categoryRepository.existsByTypeAndCode(req.getType(), req.getCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                    "이미 있는 코드입니다: " + req.getCode());
        }

        int sortOrder = req.getSortOrder() != null
                ? req.getSortOrder()
                : categoryRepository.findMaxSortOrder(req.getType()) + 1;

        SkuCategory saved = categoryRepository.save(SkuCategory.builder()
                .type(req.getType())
                .code(req.getCode())
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

    @Transactional
    public void deactivate(Long id) {
        SkuCategory category = getOrThrow(id);
        category.deactivate();
        log.info("카테고리 비활성화: type={}, code={}", category.getType(), category.getCode());
    }

    private SkuCategory getOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "카테고리를 찾을 수 없습니다."));
    }
}
