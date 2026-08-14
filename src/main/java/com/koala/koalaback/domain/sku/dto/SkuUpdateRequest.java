package com.koala.koalaback.domain.sku.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SkuUpdateRequest(
        @NotBlank(message = "name은 필수입니다.")
        String name,

        String description,

        @NotNull(message = "price는 필수입니다.")
        @Positive(message = "price는 0보다 커야 합니다.")
        BigDecimal price,

        @Min(value = 0, message = "stock은 0 이상이어야 합니다.")
        int stock
) {
}
