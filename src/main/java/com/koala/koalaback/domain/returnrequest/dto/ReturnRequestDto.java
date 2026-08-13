package com.koala.koalaback.domain.returnrequest.dto;

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

    @Getter
    public static class CreateRequest {
        @NotBlank
        private String orderNo;      // 반품 대상 주문번호

        @NotBlank
        private String returnType;   // RETURN | EXCHANGE

        @NotBlank
        private String reason;       // SIMPLE_CHANGE | DEFECT | WRONG_DELIVERY | OTHER

        private String reasonDetail; // 상세 사유 (선택)
    }

    /** 관리자 처리 (승인/거절) */
    @Getter @Setter
    public static class AdminProcessRequest {
        @NotBlank
        @Pattern(regexp = "APPROVE|REJECT", message = "action 은 APPROVE 또는 REJECT 여야 합니다.")
        private String action;

        /**
         * 승인 시 환불 금액. 비우면 전액.
         *
         * <p>상한(주문 총액)은 값을 알아야 검사할 수 있어 서비스에서 본다.
         * 여기서는 형식만 막는다 — 0 이하는 어떤 주문에서도 말이 되지 않는다.
         */
        @DecimalMin(value = "1", message = "환불 금액은 1원 이상이어야 합니다.")
        private BigDecimal refundAmount;

        private String adminMemo;
    }

    @Getter
    @Builder
    public static class ReturnResponse {
        private Long   id;
        private String returnNo;
        private String orderNo;
        private String returnType;
        private String reason;
        private String reasonDetail;
        private String status;
        private BigDecimal refundAmount;
        private String adminMemo;
        private LocalDateTime processedAt;
        private LocalDateTime createdAt;

        // 관리자용 추가 필드
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
