package com.koala.koalaback.domain.sku.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class SkuCsvDto {
    @Getter @Setter
    public static class Row {
        private int rowNumber;

        private String artistCode;
        private String name;
        private String slug;
        private String description;
        private String mainCategory;
        private String genre;
        private String material;
        private String materialDescription;
        private String packagingTitle;
        private String packagingDescription;
        private String listPrice;
        private String salePrice;
        private String editionSize;
        private String editionNumber;
        private String primaryImageUrl;
        private String widthCm;
        private String heightCm;
        private String depthCm;
        private String weightKg;
        private String initialStock;
    }

    @Getter
    @Builder
    public static class ParsedRow {
        private int rowNumber;
        private Long artistId;
        private SkuDto.CreateRequest request;
        private int initialStock;
    }

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

    @Getter
    @Builder
    public static class ImportResult {
        private int totalRows;
        private int succeeded;
        private List<RowError> errors;

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
