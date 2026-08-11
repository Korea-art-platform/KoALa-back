package com.koala.koalaback.domain.sku.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class SkuCsvDto {

    // ── csv 원본 행 (전부 문자열) ─────────────────────────
    @Getter @Setter
    public static class Row {
        private int rowNumber;   // 엑셀에서 보이는 줄 번호

        private String artistCode;
        private String name;
        private String slug;
        private String description;
        private String skuType;
        private String genre;
        private String material;
        private String materialDescription;
        private String packagingTitle;
        private String packagingDescription;
        private String listPrice;
        private String salePrice;
        private String isLimitedEdition;
        private String editionSize;
        private String editionNumber;
        private String primaryImageUrl;
        private String widthCm;
        private String heightCm;
        private String depthCm;
        private String weightKg;
        private String initialStock;
    }

    // ── 검증 통과분 (타입 변환 완료) ──────────────────────
    @Getter
    @Builder
    public static class ParsedRow {
        private int rowNumber;
        private Long artistId;
        private SkuDto.CreateRequest request;
        private int initialStock;
    }

    // ── 행 오류 ──────────────────────────────────────────
    @Getter
    @Builder
    public static class RowError {
        private int rowNumber;
        private String field;
        private Object rejectedValue;
        private String reason;

        public static RowError of(int rowNumber, String field,
                                  Object rejectedValue, String reason) {
            return RowError.builder()
                    .rowNumber(rowNumber)
                    .field(field)
                    .rejectedValue(rejectedValue)
                    .reason(reason)
                    .build();
        }
    }

    // ── 사전검증 결과 (저장 전) ───────────────────────────
    // ImportResult 는 저장까지 끝난 최종 리포트라 이 단계에서 만들 수 없다.
    // 검증기는 통과분과 오류를 함께 돌려줘야 해서 별도 타입을 둔다.
    @Getter
    @Builder
    public static class ValidationResult {
        private List<ParsedRow> validRows;
        private List<RowError> errors;

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public static ValidationResult of(List<ParsedRow> validRows, List<RowError> errors) {
            return ValidationResult.builder()
                    .validRows(validRows)
                    .errors(errors)
                    .build();
        }
    }

    // ── 최종 리포트 ──────────────────────────────────────
    @Getter
    @Builder
    public static class ImportResult {
        private int totalRows;
        private int succeeded;
        private List<RowError> errors;

        // 실패 건수는 오류 개수가 아니라 오류가 난 행 수
        public int getFailed() {
            return (int) errors.stream()
                    .map(RowError::getRowNumber)
                    .distinct()
                    .count();
        }

        public static ImportResult of(int totalRows, int succeeded, List<RowError> errors) {
            return ImportResult.builder()
                    .totalRows(totalRows)
                    .succeeded(succeeded)
                    .errors(errors)
                    .build();
        }
    }
}
