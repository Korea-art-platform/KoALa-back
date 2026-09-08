package com.koala.koalaback.domain.inquiry.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.inquiry.entity.Inquiry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class InquiryDto {
    @Getter
    @Schema(name = "InquiryCreateRequest", description = "1:1 문의 등록")
    public static class CreateRequest {
        @NotBlank
        @Size(max = 200)
        @Schema(description = "제목. 200자까지", requiredMode = Schema.RequiredMode.REQUIRED)
        private String title;

        @NotBlank
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String content;

        @NotBlank
        @Schema(description = "문의 분류", requiredMode = Schema.RequiredMode.REQUIRED)
        private String category;

        @Schema(description = "주문에 대한 문의면 주문번호를 넣는다. 내 주문이어야 한다. "
                + "그냥 물어보는 것이면 비운다", example = "KL-20260907143000-A1B2")
        private String orderNo;

        @Schema(description = "비밀글로 할지", example = "false")
        private Boolean isSecret;
    }

    @Getter
    @Schema(description = "문의 답변. 답변이 달리면 고객은 그 문의를 지울 수 없다")
    public static class AnswerRequest {
        @NotBlank
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String answerContent;
    }

    @Getter
    @Builder
    @Schema(description = "문의")
    public static class InquiryResponse {
        private Long          id;
        private String        inquiryCode;
        private Long          userId;
        private String        userName;
        private String        userEmail;

        @Schema(description = "주문에 붙은 문의면 그 주문 id, 아니면 null")
        private Long          orderId;

        @Schema(example = "KL-20260907143000-A1B2")
        private String        orderNo;

        private String        category;
        private String        title;
        private String        content;

        @Schema(description = "상태. PENDING(답변 대기) · ANSWERED(답변 완료) · CLOSED(종료)", example = "PENDING")
        private String        status;

        @Schema(description = "비밀글인지", example = "false")
        private Boolean       isSecret;

        @Schema(description = "답변 내용. 답변 전에는 null")
        private String        answerContent;

        @Schema(description = "답변한 관리자 이름")
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
