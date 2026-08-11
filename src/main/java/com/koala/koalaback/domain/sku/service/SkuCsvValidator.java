package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.entity.SkuGenre;
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

    // ck_skus_type — 위반 시 INSERT 가 실패한다
    private static final Set<String> SKU_TYPES = Set.of("ARTWORK", "GOODS");
    private static final String DEFAULT_SKU_TYPE = "ARTWORK";

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

        // 1단계 — 행 하나만 보고 판단 가능한 것
        List<Draft> drafts = new ArrayList<>();
        for (SkuCsvDto.Row row : rows) {
            Draft draft = validateRow(row, errors);
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
    private Draft validateRow(SkuCsvDto.Row row, List<SkuCsvDto.RowError> errors) {
        int rowNumber = row.getRowNumber();
        List<SkuCsvDto.RowError> rowErrors = new ArrayList<>();

        String name = requireText(row.getName(), "name", MAX_NAME, rowNumber, rowErrors);
        String slug = validateSlug(row.getSlug(), rowNumber, rowErrors);
        String artistCode = requireText(row.getArtistCode(), "artistCode", 40, rowNumber, rowErrors);

        String skuType = validateEnum(row.getSkuType(), "skuType", SKU_TYPES,
                DEFAULT_SKU_TYPE, rowNumber, rowErrors);
        String genre = validateGenre(row.getGenre(), rowNumber, rowErrors);

        // decimal(13,2) — 정수부 11자리까지
        BigDecimal listPrice = parseDecimal(row.getListPrice(), "listPrice",
                11, 2, true, rowNumber, rowErrors);
        BigDecimal salePrice = parseDecimal(row.getSalePrice(), "salePrice",
                11, 2, false, rowNumber, rowErrors);

        if (listPrice != null && salePrice != null && salePrice.compareTo(listPrice) > 0) {
            rowErrors.add(SkuCsvDto.RowError.of(rowNumber, "salePrice", row.getSalePrice(),
                    "판매가가 정가보다 클 수 없습니다. (정가 " + listPrice.toPlainString() + ")"));
        }

        Boolean isLimited = parseBoolean(row.getIsLimitedEdition(), "isLimitedEdition",
                rowNumber, rowErrors);
        Integer editionSize = parseInt(row.getEditionSize(), "editionSize", rowNumber, rowErrors);
        Integer editionNumber = parseInt(row.getEditionNumber(), "editionNumber", rowNumber, rowErrors);
        validateEdition(isLimited, editionSize, editionNumber, rowNumber, rowErrors);

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
        request.setSkuType(skuType);
        request.setGenre(genre);
        request.setMaterial(material);
        request.setMaterialDescription(row.getMaterialDescription());
        request.setPackagingTitle(packagingTitle);
        request.setPackagingDescription(row.getPackagingDescription());
        request.setListPrice(listPrice);
        request.setSalePrice(salePrice);
        request.setIsLimitedEdition(isLimited);
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

    /** 비어 있으면 기본값. 값이 있으면 허용 목록에 있어야 한다 */
    private String validateEnum(String value, String field, Set<String> allowed, String defaultValue,
                                int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) return defaultValue;

        String upper = value.toUpperCase();
        if (!allowed.contains(upper)) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, field, value,
                    "허용값: " + String.join(", ", allowed)));
            return null;
        }
        return upper;
    }

    private String validateGenre(String value, int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) return SkuGenre.DEFAULT.name();

        String upper = value.toUpperCase();
        if (!SkuGenre.isValid(upper)) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "genre", value,
                    "허용값: " + String.join(", ", SkuGenre.codes())));
            return null;
        }
        return upper;
    }

    /** ck_skus_edition 은 양방향이다 — 한정판이 아닌데 에디션 값이 있어도 INSERT 가 실패한다 */
    private void validateEdition(Boolean isLimited, Integer size, Integer number,
                                 int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (isLimited == null) return;   // 이미 파싱 오류가 기록됨

        if (!isLimited) {
            if (size != null || number != null) {
                errors.add(SkuCsvDto.RowError.of(rowNumber, "editionSize", size,
                        "한정판이 아니면 editionSize·editionNumber 를 비워야 합니다."));
            }
            return;
        }

        if (size == null || number == null) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "editionSize", size,
                    "한정판이면 editionSize 와 editionNumber 가 모두 필요합니다."));
            return;
        }
        if (size <= 0 || number <= 0) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "editionSize", size,
                    "에디션 번호와 총 수량은 1 이상이어야 합니다."));
            return;
        }
        if (number > size) {
            errors.add(SkuCsvDto.RowError.of(rowNumber, "editionNumber", number,
                    "에디션 번호가 총 수량(" + size + ")보다 클 수 없습니다."));
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

    /** 비어 있으면 false. 엑셀에서 흔한 표기를 모두 받는다 */
    private Boolean parseBoolean(String value, String field,
                                 int rowNumber, List<SkuCsvDto.RowError> errors) {
        if (value == null) return Boolean.FALSE;

        String v = value.trim().toLowerCase();
        if (Set.of("true", "1", "y", "yes", "o").contains(v))  return Boolean.TRUE;
        if (Set.of("false", "0", "n", "no", "x").contains(v))  return Boolean.FALSE;

        errors.add(SkuCsvDto.RowError.of(rowNumber, field, value,
                "true / false 로 입력해주세요."));
        return null;
    }

    /** 엑셀이 넣는 천 단위 구분자·공백 제거 */
    private String stripNumberFormat(String value) {
        return value.replace(",", "").replace(" ", "").trim();
    }
}
