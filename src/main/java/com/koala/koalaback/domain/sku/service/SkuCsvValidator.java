package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * csv 행 사전검증. db 는 읽기만 하고 쓰지 않는다.
 *
 * <p>오류를 만나도 예외를 던지지 않고 {@link SkuCsvDto.RowError} 로 모은다.
 * 첫 오류에서 중단하면 관리자가 한 줄 고치고 재업로드하는 왕복을 반복하게 된다.
 *
 * <p><b>여기서 놓친 제약은 저장 도중 SQL 예외가 되고, 앞 청크는 이미 커밋된 뒤라 부분 등록이 된다.</b>
 * 그래서 DB 제약(길이·정밀도·CHECK)을 빠짐없이 앞당겨 검사한다.
 */
@Component
@RequiredArgsConstructor
public class SkuCsvValidator {

    private final SkuRepository skuRepository;
    private final ArtistRepository artistRepository;
    private final SkuCategoryService categoryService;

    // url 로 쓰이므로 소문자·숫자·하이픈만. 하이픈 연속/양끝 금지
    private static final Pattern SLUG_PATTERN =
            Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    // varchar 길이
    private static final int MAX_NAME = 200;
    private static final int MAX_SLUG = 220;
    private static final int MAX_MATERIAL = 300;
    private static final int MAX_PACKAGING_TITLE = 200;
    private static final int MAX_IMAGE_URL = 700;

    /** 1단계까지 통과한 행. artistId 는 3단계에서 채워진다 */
    private record Draft(int rowNumber, String artistCode,
                         SkuDto.CreateRequest request, int initialStock) {
        String slug() { return request.getSlug(); }
    }

    public SkuCsvDto.ValidationResult validate(List<SkuCsvDto.Row> rows) {
        List<SkuCsvDto.RowError> errors = new ArrayList<>();

        // 카테고리는 행마다 조회하지 않고 한 번만 읽는다 (쿼리 1회)
        Map<String, Set<String>> categoryCodes = categoryService.getActiveCodesByType();

        // 1단계 — 행 하나만 보고 판단 가능한 것
        List<Draft> drafts = new ArrayList<>();
        for (SkuCsvDto.Row row : rows) {
            Draft draft = validateRow(row, categoryCodes, errors);
            if (draft != null) drafts.add(draft);
        }

        // 2단계 — 파일 안에서의 중복
        drafts = rejectDuplicateSlugs(drafts, errors);

        // 3단계 — db 대조 (쿼리 2회)
        List<SkuCsvDto.ParsedRow> validRows = crossCheckWithDb(drafts, errors);

        return SkuCsvDto.ValidationResult.of(validRows, errors);
    }

    // ── 1단계: 행 단위 ───────────────────────────────────

    /** 오류가 하나라도 있으면 null 을 반환해 이후 단계에서 제외한다 */
    private Draft validateRow(SkuCsvDto.Row row,
                              Map<String, Set<String>> categoryCodes,
                              List<SkuCsvDto.RowError> errors) {
        int rowNumber = row.getRowNumber();
        List<SkuCsvDto.RowError> rowErrors = new ArrayList<>();

        String name = requireText(row.getName(), "name", MAX_NAME, rowNumber, rowErrors);
        String slug = validateSlug(row.getSlug(), rowNumber, rowErrors);
        String artistCode = requireText(row.getArtistCode(), "artistCode", 40, rowNumber, rowErrors);

        String mainCategory = validateCategory(row.getMainCategory(), "mainCategory",
                categoryCodes.get(SkuCategory.TYPE_MAIN), rowNumber, rowErrors);
        String genre = validateCategory(row.getGenre(), "genre",
                categoryCodes.get(SkuCategory.TYPE_SUB), rowNumber, rowErrors);

        // decimal(13,2) — 정수부 11자리까지
        BigDecimal listPrice = parseDecimal(row.getListPrice(), "listPrice",
                11, 2, true, rowNumber, rowErrors);
        BigDecimal salePrice = parseDecimal(row.getSalePrice(), "salePrice",
                11, 2, false, rowNumber, rowErrors);

        if (listPrice != null && salePrice != null && salePrice.compareTo(listPrice) > 0) {
            rowErrors.add(SkuCsvDto.RowError.of(rowNumber, "salePrice", row.getSalePrice(),
                    "판매가가 정가보다 클 수 없습니다. (정가 " + listPrice.toPlainString() + ")"));
        }

        Integer editionSize = parseInt(row.getEditionSize(), "editionSize", rowNumber, rowErrors);
        Integer editionNumber = parseInt(row.getEditionNumber(), "editionNumber", rowNumber, rowErrors);
        validateEdition(mainCategory, editionSize, editionNumber, rowNumber, rowErrors);

        Integer initialStock = parseInt(row.getInitialStock(), "initialStock", rowNumber, rowErrors);
        if (initialStock != null && initialStock < 0) {
            rowErrors.add(SkuCsvDto.RowError.of(rowNumber, "initialStock", row.getInitialStock(),
                    "재고는 음수일 수 없습니다."));
        }

        // decimal(10,2) / decimal(10,3)
        BigDecimal widthCm  = parseDecimal(row.getWidthCm(),  "widthCm",  8, 2, false, rowNumber, rowErrors);
        BigDecimal heightCm = parseDecimal(row.getHeightCm(), "heightCm", 8, 2, false, rowNumber, rowErrors);
        BigDecimal depthCm  = parseDecimal(row.getDepthCm(),  "depthCm",  8, 2, false, rowNumber, rowErrors);
        BigDecimal weightKg = parseDecimal(row.getWeightKg(), "weightKg", 7, 3, false, rowNumber, rowErrors);

        String material = limitText(row.getMaterial(), "material", MAX_MATERIAL, rowNumber, rowErrors);
        String packagingTitle = limitText(row.getPackagingTitle(), "packagingTitle",
                MAX_PACKAGING_TITLE, rowNumber, rowErrors);
        String primaryImageUrl = limitText(row.getPrimaryImageUrl(), "primaryImageUrl",
                MAX_IMAGE_URL, rowNumber, rowErrors);

        if (!rowErrors.isEmpty()) {
            errors.addAll(rowErrors);
            return null;
        }

        SkuDto.CreateRequest request = new SkuDto.CreateRequest();
        request.setArtistCode(artistCode);
        request.setName(name);
        request.setSlug(slug);
        request.setDescription(row.getDescription());
        request.setMainCategory(mainCategory);
        request.setGenre(genre);
        request.setMaterial(material);
        request.setMaterialDescription(row.getMaterialDescription());
        request.setPackagingTitle(packagingTitle);
        request.setPackagingDescription(row.getPackagingDescription());
        request.setListPrice(listPrice);
        request.setSalePrice(salePrice);
        request.setEditionSize(editionSize);
        request.setEditionNumber(editionNumber);
        request.setPrimaryImageUrl(primaryImageUrl);
        request.setWidthCm(widthCm);
        request.setHeightCm(heightCm);
        request.setDepthCm(depthCm);
        request.setWeightKg(weightKg);

        return new Draft(rowNumber, artistCode, request,
                initialStock != null ? initialStock : 0);
    }

    // ── 2단계: 파일 내 중복 ──────────────────────────────

    /** 같은 slug 가 여러 행에 있으면 그 행들을 전부 제외한다 (어느 쪽이 맞는지 알 수 없다) */
    private List<Draft> rejectDuplicateSlugs(List<Draft> drafts,
                                             List<SkuCsvDto.RowError> errors) {
        Map<String, Long> countBySlug = drafts.stream()
                .collect(Collectors.groupingBy(Draft::slug, Collectors.counting()));

        List<Draft> unique = new ArrayList<>();
        for (Draft draft : drafts) {
            if (countBySlug.get(draft.slug()) > 1) {
                errors.add(SkuCsvDto.RowError.of(draft.rowNumber(), "slug", draft.slug(),
                        "파일 안에서 slug 가 중복됩니다."));
            } else {
                unique.add(draft);
            }
        }
        return unique;
    }

    // ── 3단계: db 대조 ───────────────────────────────────

    /** 행 수와 무관하게 쿼리 2회 — slug IN, artistCode IN */
    private List<SkuCsvDto.ParsedRow> crossCheckWithDb(List<Draft> drafts,
                                                       List<SkuCsvDto.RowError> errors) {
        if (drafts.isEmpty()) return List.of();

        List<String> slugs = drafts.stream().map(Draft::slug).toList();
        // soft delete 된 상품도 uk_skus_slug 는 살아 있다 — deletedAt 조건을 넣으면 INSERT 에서 터진다
        Set<String> existingSlugs = new HashSet<>(skuRepository.findExistingSlugs(slugs));

        List<String> artistCodes = drafts.stream()
                .map(Draft::artistCode).distinct().toList();
        Map<String, Long> artistIdByCode = artistRepository.findAllByArtistCodeIn(artistCodes)
                .stream()
                .collect(Collectors.toMap(Artist::getArtistCode, Artist::getId));

        List<SkuCsvDto.ParsedRow> validRows = new ArrayList<>();
        for (Draft draft : drafts) {
            boolean ok = true;

            if (existingSlugs.contains(draft.slug())) {
                errors.add(SkuCsvDto.RowError.of(draft.rowNumber(), "slug", draft.slug(),
                        "이미 등록된 slug 입니다. 덮어쓰지 않으니 다른 값으로 바꿔주세요."));
                ok = false;
            }

            Long artistId = artistIdByCode.get(draft.artistCode());
            if (artistId == null) {
                errors.add(SkuCsvDto.RowError.of(draft.rowNumber(), "artistCode", draft.artistCode(),
                        "존재하지 않는 작가 코드입니다."));
                ok = false;
            }

            if (ok) {
                validRows.add(SkuCsvDto.ParsedRow.builder()
                        .rowNumber(draft.rowNumber())
                        .artistId(artistId)
                        .request(draft.request())
                        .initialStock(draft.initialStock())
                        .build());
            }
        }
        return validRows;
    }

    // ── 필드 검증 ────────────────────────────────────────

    private String requireText(String value, String field, int max,
                               int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, null, "필수 항목입니다."));
            return null;
        }
        return limitText(value, field, max, rowNumber, errors);
    }

    private String limitText(String value, String field, int max,
                             int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) return null;
        if (value.length() > max) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value.length() + "자",
                    max + "자까지 입력할 수 있습니다."));
            return null;
        }
        return value;
    }

    private String validateSlug(String value, int rowNumber, List<SkuCsvDto.RowError> errors) {
        String slug = requireText(value, "slug", MAX_SLUG, rowNumber, errors);
        if (slug == null) return null;

        if (!SLUG_PATTERN.matcher(slug).matches()) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "slug", slug,
                    "영문 소문자·숫자·하이픈만 쓸 수 있습니다. (예: blue-bear-01)"));
            return null;
        }
        return slug;
    }

    /**
     * 카테고리 코드 검증 — 허용값은 상수가 아니라 {@code sku_categories} 에서 온다.
     *
     * <p>관리자가 카테고리를 추가하면 배포 없이 바로 csv 에서 쓸 수 있어야 한다.
     * 비활성 카테고리는 목록에 없으므로 자동으로 거부된다.
     */
    private String validateCategory(String value, String field, Set<String> allowed,
                                    int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, null, "필수 항목입니다."));
            return null;
        }
        if (allowed == null) allowed = Set.of();

        String upper = value.toUpperCase();
        if (!allowed.contains(upper)) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value,
                    "등록되지 않은 카테고리입니다. 사용 가능: "
                            + allowed.stream().sorted().collect(Collectors.joining(", "))));
            return null;
        }
        return upper;
    }

    /**
     * 에디션 값 검증.
     *
     * <p>에디션은 한정판에만 붙는다. 총 수량·번호 모두 선택이지만, 번호만 있고
     * 총 수량이 없으면 ck_skus_edition 위반으로 INSERT 가 실패한다.
     */
    private void validateEdition(String mainCategory, Integer size, Integer number,
                                 int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (mainCategory == null) return;   // 이미 오류가 기록됨

        if (!Sku.MAIN_LIMITED.equals(mainCategory)) {
            if (size != null || number != null) {
                errors.add(SkuCsvDto.RowError.of(rowNumber, "editionSize", size,
                        "에디션 정보는 한정판(" + Sku.MAIN_LIMITED + ") 상품에만 입력할 수 있습니다."));
            }
            return;
        }

        if (number != null && size == null) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "editionSize", null,
                    "editionNumber 를 입력하려면 editionSize 도 함께 입력해야 합니다."));
            return;
        }
        if (size != null && size <= 0) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "editionSize", size,
                    "에디션 총 수량은 1 이상이어야 합니다."));
            return;
        }
        if (number != null && (number <= 0 || number > size)) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "editionNumber", number,
                    "에디션 번호는 1 이상, 총 수량(" + size + ") 이하여야 합니다."));
        }
    }

    // ── 타입 변환 ────────────────────────────────────────

    /**
     * @param intDigits 정수부 최대 자릿수 (decimal(13,2) 이면 11)
     * @param scale     소수부 최대 자릿수. 넘으면 반올림하지 않고 오류로 돌린다 — 금액이 조용히 바뀌면 안 된다
     */
    private BigDecimal parseDecimal(String value, String field, int intDigits, int scale,
                                    boolean required, int rowNumber,
                                    List<SkuCsvDto.RowError> errors) {
        if (value == null) {
            if (required) {
                errors.add(SkuCsvDto.RowError.of(rowNumber, field, null, "필수 항목입니다."));
            }
            return null;
        }

        BigDecimal parsed;
        try {
            parsed = new BigDecimal(stripNumberFormat(value));
        } catch (NumberFormatException e) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value, "숫자 형식이 아닙니다."));
            return null;
        }

        if (parsed.signum() < 0) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value, "0 이상이어야 합니다."));
            return null;
        }
        if (parsed.scale() > scale) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value,
                    "소수점 " + scale + "자리까지 입력할 수 있습니다."));
            return null;
        }
        if (parsed.precision() - parsed.scale() > intDigits) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value,
                    "값이 너무 큽니다. (정수부 " + intDigits + "자리까지)"));
            return null;
        }
        return parsed;
    }

    private Integer parseInt(String value, String field,
                            int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) return null;
        try {
            return Integer.valueOf(stripNumberFormat(value));
        } catch (NumberFormatException e) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value, "정수가 아닙니다."));
            return null;
        }
    }

    /** 엑셀이 넣는 천 단위 구분자·공백 제거 */
    private String stripNumberFormat(String value) {
        return value.replace(",", "").replace(" ", "").trim();
    }
}
