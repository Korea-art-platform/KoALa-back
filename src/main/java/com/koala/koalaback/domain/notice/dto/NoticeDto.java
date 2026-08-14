package com.koala.koalaback.domain.notice.dto;

import com.koala.koalaback.domain.notice.entity.Notice;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class NoticeDto {
    @Getter
    public static class CreateRequest {
        @NotBlank
        private String title;

        @NotBlank
        private String content;

        private Boolean isPinned;
    }

    @Getter
    public static class UpdateRequest {
        @NotBlank
        private String title;

        @NotBlank
        private String content;

        private Boolean isPinned;
    }

    @Getter
    @Builder
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
