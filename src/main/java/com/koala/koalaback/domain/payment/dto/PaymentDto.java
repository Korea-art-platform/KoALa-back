package com.koala.koalaback.domain.payment.dto;

import com.koala.koalaback.domain.payment.entity.Payment;
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
    public static class PrepareRequest {
        @NotBlank
        private String orderNo;

        @NotBlank
        private String provider;

        @NotBlank
        private String method;
    }

    @Getter
    public static class ConfirmRequest {
        @NotBlank
        private String paymentKey;

        @NotBlank
        private String orderNo;

        @NotNull
        private BigDecimal amount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelRequest {
        @NotBlank
        private String reason;

        // null 이면 전액 취소, 값이 있으면 부분취소 금액(양수만 허용)
        @Positive
        private BigDecimal cancelAmount;
    }

    @Getter
    @Builder
    public static class PrepareResponse {
        private String paymentNo;
        private String orderNo;
        private BigDecimal amount;
        private String provider;
        private String method;
    }

    @Getter
    @Builder
    public static class PaymentResponse {
        private Long id;
        private String paymentNo;
        private String orderNo;
        private String provider;
        private String method;
        private String status;
        private BigDecimal requestedAmount;
        private BigDecimal approvedAmount;
        private BigDecimal cancelledAmount;
        private String currency;
        private LocalDateTime approvedAt;
        private LocalDateTime failedAt;
        private LocalDateTime cancelledAt;

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
                    .build();
        }
    }
}