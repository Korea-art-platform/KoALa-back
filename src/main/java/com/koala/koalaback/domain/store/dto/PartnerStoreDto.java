package com.koala.koalaback.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.store.entity.PartnerStore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class PartnerStoreDto {

    @Getter
    @Schema(description = "정렬 순서 변경. 보낸 순서대로 0 부터 다시 매기므로 한 곳만 옮겨도 전체를 보내야 한다")
    public static class ReorderRequest {
        @NotEmpty
        private List<String> storeCodes;
    }

    @Getter
    @Schema(name = "StoreCreateRequest", description = "매장 등록. 우편번호·주소·연락처는 AES-GCM 으로 암호화해 저장한다")
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
        private String snsUrl;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Getter
    @Schema(name = "StoreUpdateRequest", description = "매장 수정")
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
        private String snsUrl;
        private String imageUrl;
        private Integer sortOrder;
    }

    @Getter
    @Builder
    @Schema(description = "매장. 고객용 목록은 운영 중인 곳만 정렬 순서대로 내려간다")
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
        private String snsUrl;
        private String imageUrl;
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
                    .snsUrl(s.getSnsUrl())
                    .imageUrl(s.getImageUrl())
                    .isActive(s.getIsActive())
                    .sortOrder(s.getSortOrder())
                    .createdAt(s.getCreatedAt())
                    .updatedAt(s.getUpdatedAt())
                    .build();
        }
    }
}
