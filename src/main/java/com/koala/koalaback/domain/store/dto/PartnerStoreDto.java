package com.koala.koalaback.domain.store.dto;

import com.koala.koalaback.domain.store.entity.PartnerStore;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class PartnerStoreDto {

    @Getter
    public static class CreateRequest {
        @NotBlank
        private String name;
        private String zipCode;
        @NotBlank
        private String address;
        private String addressDetail;
        @NotBlank
        private String phone;
        private String phone2;
        private String email;
        private String description;
        private String mapUrl;
        private Integer sortOrder;
    }

    @Getter
    public static class UpdateRequest {
        @NotBlank
        private String name;
        private String zipCode;
        @NotBlank
        private String address;
        private String addressDetail;
        @NotBlank
        private String phone;
        private String phone2;
        private String email;
        private String description;
        private String mapUrl;
        private Integer sortOrder;
    }

    @Getter
    @Builder
    public static class StoreResponse {
        private Long id;
        private String storeCode;
        private String name;
        private String zipCode;
        private String address;
        private String addressDetail;
        private String phone;
        private String phone2;
        private String email;
        private String description;
        private String mapUrl;
        private Boolean isActive;
        private Integer sortOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static StoreResponse from(PartnerStore s) {
            return StoreResponse.builder()
                    .id(s.getId())
                    .storeCode(s.getStoreCode())
                    .name(s.getName())
                    .zipCode(s.getZipCode())
                    .address(s.getAddress())
                    .addressDetail(s.getAddressDetail())
                    .phone(s.getPhone())
                    .phone2(s.getPhone2())
                    .email(s.getEmail())
                    .description(s.getDescription())
                    .mapUrl(s.getMapUrl())
                    .isActive(s.getIsActive())
                    .sortOrder(s.getSortOrder())
                    .createdAt(s.getCreatedAt())
                    .updatedAt(s.getUpdatedAt())
                    .build();
        }
    }
}
