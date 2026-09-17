package com.koala.koalaback.domain.popup.dto;

import com.koala.koalaback.domain.popup.entity.Popup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class PopupDto {
    @Getter
    @Schema(name = "PopupRequest", description = """
            팝업 등록·수정. 형식 검사는 서비스에서 한다 — 이미지 방식이면 imageUrl 필수,
            바로가기 버튼을 켜면 landingUrl 필수, landingUrl 은 http(s) 절대 주소나 "/" 로
            시작하는 내부 경로만 받는다.
            """)
    public static class PopupRequest {
        @Schema(description = "제목. 최대 200자", requiredMode = Schema.RequiredMode.REQUIRED)
        private String title;

        @Schema(description = "노출 여부")
        private Boolean active;

        @Schema(description = "\"오늘 하루 보지 않기\" 노출 여부")
        private Boolean showDismiss;

        @Schema(description = "고객 화면 언어", allowableValues = {"ko", "en"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private String language;

        @Schema(description = "표시 방식", allowableValues = {"IMAGE", "TEMPLATE"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private String displayType;

        @Schema(description = "이미지 주소. IMAGE 방식이면 필수", nullable = true)
        private String imageUrl;

        @Schema(description = "TEMPLATE 방식 본문. 최대 2000자", nullable = true)
        private String body;

        @Schema(description = "바로가기 버튼 노출 여부")
        private Boolean showLinkButton;

        @Schema(description = "노출 위치", allowableValues = {"HOME", "ALL"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private String placement;

        @Schema(description = "바로가기 주소. http(s) 절대 주소 또는 \"/\" 로 시작하는 내부 경로", nullable = true)
        private String landingUrl;

        @Schema(description = "정렬 순서. 작을수록 먼저. 비우면 0")
        private Integer sortOrder;
    }

    @Getter
    @Builder
    @Schema(description = "팝업 (어드민). 삭제되지 않은 팝업은 노출 여부와 무관하게 전부 내려간다")
    public static class PopupResponse {
        private String popupCode;
        private String title;
        private Boolean active;
        private Boolean showDismiss;
        private String language;
        private String displayType;
        private String imageUrl;
        private String body;
        private Boolean showLinkButton;
        private String placement;
        private String landingUrl;
        private Integer sortOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static PopupResponse from(Popup p) {
            return PopupResponse.builder()
                    .popupCode(p.getPopupCode())
                    .title(p.getTitle())
                    .active(p.getIsActive())
                    .showDismiss(p.getShowDismiss())
                    .language(p.getLanguage())
                    .displayType(p.getDisplayType())
                    .imageUrl(p.getImageUrl())
                    .body(p.getBody())
                    .showLinkButton(p.getShowLinkButton())
                    .placement(p.getPlacement())
                    .landingUrl(p.getLandingUrl())
                    .sortOrder(p.getSortOrder())
                    .createdAt(p.getCreatedAt())
                    .updatedAt(p.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "고객 화면용 팝업. 노출 중인 것만 정렬 순서대로 내려간다")
    public static class PublicPopupResponse {
        private String popupCode;
        private String title;
        private String displayType;
        private String imageUrl;
        private String body;
        private Boolean showDismiss;
        private Boolean showLinkButton;
        private String landingUrl;

        public static PublicPopupResponse from(Popup p) {
            return PublicPopupResponse.builder()
                    .popupCode(p.getPopupCode())
                    .title(p.getTitle())
                    .displayType(p.getDisplayType())
                    .imageUrl(p.getImageUrl())
                    .body(p.getBody())
                    .showDismiss(p.getShowDismiss())
                    .showLinkButton(p.getShowLinkButton())
                    .landingUrl(p.getLandingUrl())
                    .build();
        }
    }
}
