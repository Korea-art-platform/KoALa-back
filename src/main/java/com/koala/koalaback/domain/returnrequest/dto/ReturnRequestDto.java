package com.koala.koalaback.domain.returnrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReturnRequestDto {
    @Getter @Setter
    @Schema(name = "ReturnCreateRequest",
            description = "반품·교환 신청. 배송완료된 내 주문만 신청할 수 있다")
    public static class CreateRequest {
        @NotBlank
        @Schema(description = "주문번호. DELIVERED 상태여야 한다",
                example = "KL-20260907143000-A1B2", requiredMode = Schema.RequiredMode.REQUIRED)
        private String orderNo;

        @NotBlank
        @Schema(description = "RETURN(반품) 또는 EXCHANGE(교환). 교환은 완료 시 재고를 되돌리고 "
                + "반품은 되돌리지 않는다", example = "RETURN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String returnType;

        @NotBlank
        @Schema(description = "사유", requiredMode = Schema.RequiredMode.REQUIRED)
        private String reason;

        @Schema(description = "사유 상세")
        private String reasonDetail;
    }

    @Getter @Setter
    @Schema(description = "반품·교환 승인/거절. 승인하고 환불 금액이 있으면 PG 환불이 이어진다")
    public static class AdminProcessRequest {
        @NotBlank
        @Pattern(regexp = "APPROVE|REJECT", message = "action 은 APPROVE 또는 REJECT 여야 합니다.")
        private String action;

        @DecimalMin(value = "1", message = "환불 금액은 1원 이상이어야 합니다.")
        private BigDecimal refundAmount;

        private String adminMemo;
    }

    @Getter
    @Builder
    @Schema(description = "반품·교환 신청")
    public static class ReturnResponse {
        private Long   id;

        @Schema(description = "접수번호")
        private String returnNo;

        @Schema(example = "KL-20260907143000-A1B2")
        private String orderNo;

        @Schema(description = "RETURN(반품) 또는 EXCHANGE(교환)", example = "RETURN")
        private String returnType;

        private String reason;
        private String reasonDetail;

        @Schema(description = "상태. REQUESTED(접수) · APPROVED(승인) · REJECTED(거절) · COMPLETED(완료). 거절된 건은 한 주문에 하나 제한에서 빠지므로 "
                + "다시 신청할 수 있다", example = "REQUESTED")
        private String status;

        @Schema(description = "환불 금액. 승인하면서 정한 값이다", example = "366000")
        private BigDecimal refundAmount;

        @Schema(description = "관리자 메모")
        private String adminMemo;
        private LocalDateTime processedAt;
        private LocalDateTime createdAt;

        private Long   userId;
        private String ordererName;
        private String ordererPhone;

        public static ReturnResponse from(ReturnRequest r) {
            return ReturnResponse.builder()
                    .id(r.getId())
                    .returnNo(r.getReturnNo())
                    .orderNo(r.getOrder().getOrderNo())
                    .returnType(r.getReturnType())
                    .reason(r.getReason())
                    .reasonDetail(r.getReasonDetail())
                    .status(r.getStatus())
                    .refundAmount(r.getRefundAmount())
                    .adminMemo(r.getAdminMemo())
                    .processedAt(r.getProcessedAt())
                    .createdAt(r.getCreatedAt())
                    .userId(r.getUser().getId())
                    .ordererName(r.getOrder().getOrdererName())
                    .ordererPhone(r.getOrder().getOrdererPhone())
                    .build();
        }
    }
}
