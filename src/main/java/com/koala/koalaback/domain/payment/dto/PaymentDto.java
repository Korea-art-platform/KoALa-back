package com.koala.koalaback.domain.payment.dto;

import com.koala.koalaback.domain.payment.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDto {
    @Getter
    @Schema(description = "결제 준비 요청. 청구 금액은 여기서 받지 않고 주문의 총액을 그대로 쓴다")
    public static class PrepareRequest {
        @NotBlank
        @Schema(description = "주문번호. PENDING_PAYMENT 상태여야 한다",
                example = "KL-20260907143000-A1B2", requiredMode = Schema.RequiredMode.REQUIRED)
        private String orderNo;

        @NotBlank
        @Schema(description = "결제사. TOSS · NICEPAY · PAYPLE",
                example = "NICEPAY", requiredMode = Schema.RequiredMode.REQUIRED)
        private String provider;

        @NotBlank
        @Schema(description = "결제 수단. 결제사가 정한 값을 그대로 넘긴다",
                example = "카드", requiredMode = Schema.RequiredMode.REQUIRED)
        private String method;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "결제 승인 요청. 토스처럼 프론트가 승인을 부르는 방식에서 쓴다")
    public static class ConfirmRequest {
        @NotBlank
        @Schema(description = "결제사가 준 결제키. 우리가 만들 수 없는 값이다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String paymentKey;

        @NotBlank
        @Schema(description = "주문번호", example = "KL-20260907143000-A1B2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String orderNo;

        @NotNull
        @Schema(description = "승인 금액. 저장된 요청 금액과 다르면 PAYMENT_AMOUNT_MISMATCH 로 막는다",
                example = "366000", requiredMode = Schema.RequiredMode.REQUIRED)
        private BigDecimal amount;

        public ConfirmRequest(String paymentKey, String orderNo, BigDecimal amount) {
            this.paymentKey = paymentKey;
            this.orderNo = orderNo;
            this.amount = amount;
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "결제 취소·환불 요청")
    public static class CancelRequest {
        @NotBlank
        @Schema(description = "취소 사유. 결제 이력에 남는다", example = "주문취소",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String reason;

        @Positive
        @Schema(description = "환불 금액. 비우면 승인 금액 전액. 1원 이상이어야 하고 승인 금액을 넘을 수 없다",
                example = "366000")
        private BigDecimal cancelAmount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "미확정 결제를 사람이 닫을 때 쓴다. PG 콘솔에서 실제 상태를 확인한 뒤 넣는다")
    public static class ResolveRequest {
        @NotBlank
        @Schema(description = "종결 결과. CAPTURED(승인으로 확정) · CANCELLED(취소로 확정) · FAILED(실패로 확정)",
                example = "CAPTURED", requiredMode = Schema.RequiredMode.REQUIRED)
        private String outcome;

        @Schema(description = "메모. 어드민 수동 종결 표시가 앞에 붙어 결제 이력에 남는다",
                example = "PG 콘솔에서 승인 확인함")
        private String memo;
    }

    @Getter
    @Builder
    @Schema(description = "결제 준비 결과. 결제창을 띄우는 데 필요한 값이다")
    public static class PrepareResponse {
        @Schema(description = "결제번호", example = "PAY-0123456789AB")
        private String paymentNo;

        @Schema(example = "KL-20260907143000-A1B2")
        private String orderNo;

        @Schema(description = "청구 금액. 요청이 아니라 주문의 총액이다", example = "366000")
        private BigDecimal amount;

        @Schema(example = "NICEPAY")
        private String provider;

        @Schema(example = "카드")
        private String method;
    }

    @Getter
    @Builder
    @Schema(description = "결제 상태")
    public static class PaymentResponse {
        private Long id;

        @Schema(example = "PAY-0123456789AB")
        private String paymentNo;

        @Schema(example = "KL-20260907143000-A1B2")
        private String orderNo;

        @Schema(description = "결제사. TOSS · NICEPAY · PAYPLE", example = "NICEPAY")
        private String provider;

        private String method;

        @Schema(description = "결제 상태. READY(준비) · IN_PROGRESS(승인 진행중) · CAPTURED(승인 완료) · IN_DOUBT(승인 여부 미확정) · FAILED(실패) · CANCELLED(취소) · CANCEL_IN_PROGRESS(취소 진행중)", example = "CAPTURED")
        private String status;

        @Schema(description = "청구한 금액", example = "366000")
        private BigDecimal requestedAmount;

        @Schema(description = "실제 승인된 금액. 승인 전에는 null", example = "366000")
        private BigDecimal approvedAmount;

        @Schema(description = "취소·환불된 금액의 누계", example = "0")
        private BigDecimal cancelledAmount;

        @Schema(example = "KRW")
        private String currency;

        private LocalDateTime approvedAt;
        private LocalDateTime failedAt;
        private LocalDateTime cancelledAt;

        @Schema(description = "실패·미확정 사유 코드. PG 가 준 값이거나 내부에서 붙인 값이다",
                example = "PROVIDER_EXCEPTION")
        private String failureCode;

        @Schema(description = "실패·미확정 사유")
        private String failureMessage;

        private LocalDateTime createdAt;

        public static PaymentResponse from(Payment p) {
            return PaymentResponse.builder()
                    .id(p.getId())
                    .paymentNo(p.getPaymentNo())
                    .orderNo(p.getOrder().getOrderNo())
                    .provider(p.getProvider())
                    .method(p.getMethod())
                    .status(p.getStatus())
                    .requestedAmount(p.getRequestedAmount())
                    .approvedAmount(p.getApprovedAmount())
                    .cancelledAmount(p.getCancelledAmount())
                    .currency(p.getCurrency())
                    .approvedAt(p.getApprovedAt())
                    .failedAt(p.getFailedAt())
                    .cancelledAt(p.getCancelledAt())
                    .failureCode(p.getFailureCode())
                    .failureMessage(p.getFailureMessage())
                    .createdAt(p.getCreatedAt())
                    .build();
        }
    }
}
