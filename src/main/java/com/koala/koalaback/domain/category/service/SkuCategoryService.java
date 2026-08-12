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

/**
 * 상품 카테고리 관리.
 *
 * <p>카테고리는 몇 십 개 수준이고 자주 바뀌지 않는다. 그래서 검증할 때
 * 코드 하나씩 조회하지 않고 <b>전체를 한 번 읽어 메모리에서 대조</b>한다.
 * CSV 일괄 등록에서 행마다 조회하면 5,000행 = 5,000쿼리가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkuCategoryService {

    private final SkuCategoryRepository categoryRepository;

    // ── 조회 ──────────────────────────────────────────────

    /** 공개용 — 활성 카테고리만 */
    public SkuCategoryDto.GroupedResponse getActiveCategories() {
        List<SkuCategory> all = categoryRepository.findByIsActiveTrueOrderByTypeAscSortOrderAsc();
        return group(all, Map.of());
    }

    /** 어드민용 — 비활성 포함, 사용 중인 상품 수 포함 */
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

    // ── 검증용 (상품 등록·CSV 에서 사용) ──────────────────

    /**
     * 활성 카테고리 코드 집합. 행마다 조회하지 않도록 한 번에 읽어 간다.
     *
     * @return type → 활성 code 집합
     */
    public Map<String, Set<String>> getActiveCodesByType() {
        return categoryRepository.findByIsActiveTrueOrderByTypeAscSortOrderAsc().stream()
                .collect(Collectors.groupingBy(
                        SkuCategory::getType,
                        Collectors.mapping(SkuCategory::getCode, Collectors.toSet())));
    }

    /** 단건 검증 — 상품 등록/수정에서 사용 */
    public void validateCodes(String mainCategory, String subCategory) {
        Map<String, Set<String>> codes = getActiveCodesByType();

        Set<String> mains = codes.getOrDefault(SkuCategory.TYPE_MAIN, Set.of());
        if (mainCategory == null || !mains.contains(mainCategory)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "존재하지 않는 대분류입니다: " + mainCategory);
        }

        // 소분류는 비워둘 수 있다 (등록 시점에 장르가 정해지지 않은 경우)
        if (subCategory != null && !subCategory.isBlank()) {
            Set<String> subs = codes.getOrDefault(SkuCategory.TYPE_SUB, Set.of());
            if (!subs.contains(subCategory)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "존재하지 않는 소분류입니다: " + subCategory);
            }
        }
    }

    // ── 변경 ──────────────────────────────────────────────

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

    /**
     * 비활성화 — 실제 삭제하지 않는다.
     *
     * <p>지난 주문의 상품이 어느 카테고리였는지 이력이 남아야 하고, 실수 복구도 쉽다.
     * 사용 중이어도 막지 않는다. 몇 건이 영향받는지는 목록 API 의 usedCount 로 미리 보여준다.
     */
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
