package com.koala.koalaback.domain.order.dto;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OnSitePaymentDto {

    @Getter
    @Schema(name = "OnSitePaymentCreateRequest", description = "현장결제 만들기. 금액은 고객이 실제로 내는 금액(부가세 포함)이다")
    public static class CreateRequest {
        @NotBlank @Size(max = 200)
        private String itemName;
        @NotBlank
        private String artistCode;
        @NotNull @Min(100) @Max(100_000_000)
        private Long amount;
        @Schema(description = "면세 여부. 원작처럼 부가세가 없는 품목이면 true")
        private Boolean taxExempt;
        @Size(max = 50)
        private String buyerName;
        @Size(max = 30)
        private String buyerPhone;
        @Email @Size(max = 200)
        private String buyerEmail;
        @Size(max = 255)
        private String memo;
    }
    @Getter
    @Builder
    @Schema(name = "OnSitePaymentResponse", description = "현장결제 한 건 (어드민)")
    public static class Response {
        private String orderNo;
        private String payToken;
        private String itemName;
        private String artistName;
        private BigDecimal amount;
        private String orderStatus;
        private String paymentStatus;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
        public static Response from(Order o) {
            OrderItem item = o.getOrderItems().isEmpty() ? null : o.getOrderItems().get(0);
            return Response.builder()
                    .orderNo(o.getOrderNo())
                    .payToken(o.getPayToken())
                    .itemName(item != null ? item.getSkuNameSnapshot() : null)
                    .artistName(item != null ? item.getArtistNameSnapshot() : null)
                    .amount(o.getTotalAmount())
                    .orderStatus(o.getOrderStatus())
                    .paymentStatus(o.getPaymentStatus())
                    .createdAt(o.getCreatedAt())
                    .paidAt(o.getPaidAt())
                    .build();
        }
    }
    @Getter
    @Builder
    @Schema(name = "OnSitePaymentPublicResponse", description = "결제 링크를 연 고객에게 보여 주는 정보. 연락처 같은 개인정보는 싣지 않는다")
    public static class PublicResponse {
        private String orderNo;
        private String itemName;
        private String artistName;
        private BigDecimal amount;
        private boolean payable;
        private boolean paid;
        public static PublicResponse from(Order o) {
            OrderItem item = o.getOrderItems().isEmpty() ? null : o.getOrderItems().get(0);
            return PublicResponse.builder()
                    .orderNo(o.getOrderNo())
                    .itemName(item != null ? item.getSkuNameSnapshot() : null)
                    .artistName(item != null ? item.getArtistNameSnapshot() : null)
                    .amount(o.getTotalAmount())
                    .payable("PENDING_PAYMENT".equals(o.getOrderStatus()))
                    .paid("PAID".equals(o.getPaymentStatus()))
                    .build();
        }
    }
}