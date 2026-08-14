package com.koala.koalaback.domain.inquiry.dto;

import com.koala.koalaback.domain.inquiry.entity.Inquiry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class InquiryDto {
    @Getter
    public static class CreateRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotBlank
        private String content;

        @NotBlank
        private String category;

        private String orderNo;
        private Boolean isSecret;
    }

    @Getter
    public static class AnswerRequest {
        @NotBlank
        private String answerContent;
    }

    @Getter
    @Builder
    public static class InquiryResponse {
        private Long          id;
        private String        inquiryCode;
        private Long          userId;
        private String        userName;
        private String        userEmail;
        private Long          orderId;
        private String        orderNo;
        private String        category;
        private String        title;
        private String        content;
        private String        status;
        private Boolean       isSecret;
        private String        answerContent;
        private String        answeredByName;
        private LocalDateTime answeredAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static InquiryResponse from(Inquiry q) {
            return InquiryResponse.builder()
                    .id(q.getId())
                    .inquiryCode(q.getInquiryCode())
                    .userId(q.getUser().getId())
                    .userName(q.getUser().getName())
                    .userEmail(q.getUser().getEmail())
                    .orderId(q.getOrder() != null ? q.getOrder().getId() : null)
                    .orderNo(q.getOrder() != null ? q.getOrder().getOrderNo() : null)
                    .category(q.getCategory())
                    .title(q.getTitle())
                    .content(q.getContent())
                    .status(q.getStatus())
                    .isSecret(q.getIsSecret())
                    .answerContent(q.getAnswerContent())
                    .answeredByName(q.getAnsweredBy() != null ? q.getAnsweredBy().getName() : null)
                    .answeredAt(q.getAnsweredAt())
                    .createdAt(q.getCreatedAt())
                    .updatedAt(q.getUpdatedAt())
                    .build();
        }

        public static InquiryResponse fromMasked(Inquiry q, boolean isOwnerOrAdmin) {
            InquiryResponse r = from(q);
            if (q.getIsSecret() && !isOwnerOrAdmin) {
                return InquiryResponse.builder()
                        .id(r.id)
                        .inquiryCode(r.inquiryCode)
                        .userId(r.userId)
                        .userName(r.userName)
                        .category(r.category)
                        .title("비밀글입니다.")
                        .content(null)
                        .status(r.status)
                        .isSecret(true)
                        .createdAt(r.createdAt)
                        .build();
            }
            return r;
        }
    }
}
