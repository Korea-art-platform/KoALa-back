package com.koala.koalaback.domain.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.notice.entity.Notice;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class NoticeDto {
    @Getter
    @Schema(name = "NoticeCreateRequest", description = "공지 등록")
    public static class CreateRequest {
        @NotBlank
        private String title;

        @NotBlank
        private String content;

        private Boolean isPinned;
    }

    @Getter
    @Schema(name = "NoticeUpdateRequest", description = "공지 수정")
    public static class UpdateRequest {
        @NotBlank
        private String title;

        @NotBlank
        private String content;

        private Boolean isPinned;
    }

    @Getter
    @Builder
    @Schema(description = "공지. 고객용은 내려둔 공지가 빠지고, 어드민용은 전부 내려간다")
    public static class NoticeResponse {
        private Long id;
        private String noticeCode;
        private String title;
        private String content;
        private Boolean isPinned;
        private Boolean isActive;
        private String createdByAdminName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static NoticeResponse from(Notice n) {
            return NoticeResponse.builder()
                    .id(n.getId())
                    .noticeCode(n.getNoticeCode())
                    .title(n.getTitle())
                    .content(n.getContent())
                    .isPinned(n.getIsPinned())
                    .isActive(n.getIsActive())
                    .createdByAdminName(n.getCreatedByAdmin() != null ? n.getCreatedByAdmin().getName() : null)
                    .createdAt(n.getCreatedAt())
                    .updatedAt(n.getUpdatedAt())
                    .build();
        }
    }
}
