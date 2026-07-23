package com.koala.koalaback.domain.order.dto;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {

    @Getter
    public static class CreateRequest {
        @NotBlank
        private String ordererName;

        @NotBlank
        private String ordererEmail;

        @NotBlank
        private String ordererPhone;

        @NotNull @Valid
        private ShipmentRequest shipment;

        private List<Long> cartItemIds;
    }

    @Getter
    public static class ShipmentRequest {
        @NotBlank
        private String recipientName;

        @NotBlank
        private String recipientPhone;

        @NotBlank
        private String zipCode;

        @NotBlank
        private String address1;

        private String address2;
        private String deliveryRequest;
    }

    @Getter
    public static class AdminCancelRequest {
        @NotBlank
        private String reason;            // 취소 사유 (필수)
        @Positive
        private BigDecimal cancelAmount;  // null 이면 전액 환불, 값이 있으면 양수만
    }

    @Getter
    public static class RegisterTrackingRequest {
        @NotBlank
        private String carrierCode;

        @NotBlank
        private String trackingNo;
    }

    @Getter
    @Builder
    public static class OrderSummaryResponse {
        private Long id;
        private Long userId;
        private String orderNo;
        private String orderStatus;
        private String paymentStatus;
        private BigDecimal totalAmount;
        private int itemCount;
        private String firstSkuName;
        private String firstSkuImageUrl;
        private String ordererName;
        private String ordererPhone;
        private LocalDateTime createdAt;

        public static OrderSummaryResponse from(Order o) {
            var items = o.getOrderItems();
            String firstName = items.isEmpty() ? "" : items.get(0).getSkuNameSnapshot();
            String firstImage = items.isEmpty() ? null
                    : (items.get(0).getSku() != null
                    ? items.get(0).getSku().getPrimaryImageUrl() : null);
            return OrderSummaryResponse.builder()
                    .id(o.getId())
                    .userId(o.getUser() != null ? o.getUser().getId() : null)
                    .orderNo(o.getOrderNo())
                    .orderStatus(o.getOrderStatus())
                    .paymentStatus(o.getPaymentStatus())
                    .totalAmount(o.getTotalAmount())
                    .itemCount(items.size())
                    .firstSkuName(firstName)
                    .firstSkuImageUrl(firstImage)
                    .ordererName(o.getOrdererName())
                    .ordererPhone(o.getOrdererPhone())
                    .createdAt(o.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class OrderDetailResponse {
        private Long id;
        private String orderNo;
        private String orderStatus;
        private String paymentStatus;
        private String currency;
        private BigDecimal productAmount;
        private BigDecimal discountAmount;
        private BigDecimal shippingAmount;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private String ordererName;
        private String ordererEmail;
        private String ordererPhone;
        private List<OrderItemResponse> items;
        private ShipmentResponse shipment;
        private LocalDateTime paidAt;
        private LocalDateTime cancelledAt;
        private LocalDateTime createdAt;

        public static OrderDetailResponse from(Order o) {
            return OrderDetailResponse.builder()
                    .id(o.getId())
                    .orderNo(o.getOrderNo())
                    .orderStatus(o.getOrderStatus())
                    .paymentStatus(o.getPaymentStatus())
                    .currency(o.getCurrency())
                    .productAmount(o.getProductAmount())
                    .discountAmount(o.getDiscountAmount())
                    .shippingAmount(o.getShippingAmount())
                    .taxAmount(o.getTaxAmount())
                    .totalAmount(o.getTotalAmount())
                    .ordererName(o.getOrdererName())
                    .ordererEmail(o.getOrdererEmail())
                    .ordererPhone(o.getOrdererPhone())
                    .items(o.getOrderItems().stream()
                            .map(OrderItemResponse::from).toList())
                    .shipment(o.getShipment() != null
                            ? ShipmentResponse.from(o.getShipment()) : null)
                    .paidAt(o.getPaidAt())
                    .cancelledAt(o.getCancelledAt())
                    .createdAt(o.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class OrderItemResponse {
        private Long id;
        private String skuCode;
        private String skuName;
        private String artistName;
        private String primaryImageUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotalAmount;
        private Boolean reviewWritten;

        public static OrderItemResponse from(OrderItem i) {
            return OrderItemResponse.builder()
                    .id(i.getId())
                    .skuCode(i.getSkuCodeSnapshot())
                    .skuName(i.getSkuNameSnapshot())
                    .artistName(i.getArtistNameSnapshot())
                    .primaryImageUrl(i.getSku() != null ? i.getSku().getPrimaryImageUrl() : null)
                    .quantity(i.getQuantity())
                    .unitPrice(i.getUnitPrice())
                    .lineTotalAmount(i.getLineTotalAmount())
                    .reviewWritten(i.getReviewWritten())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ShipmentResponse {
        private String recipientName;
        private String recipientPhone;
        private String zipCode;
        private String address1;
        private String address2;
        private String deliveryRequest;
        private String carrierCode;
        private String trackingNo;
        private LocalDateTime shippedAt;
        private LocalDateTime deliveredAt;

        public static ShipmentResponse from(OrderShipment s) {
            return ShipmentResponse.builder()
                    .recipientName(s.getRecipientName())
                    .recipientPhone(s.getRecipientPhone())
                    .zipCode(s.getZipCode())
                    .address1(s.getAddress1())
                    .address2(s.getAddress2())
                    .deliveryRequest(s.getDeliveryRequest())
                    .carrierCode(s.getCarrierCode())
                    .trackingNo(s.getTrackingNo())
                    .shippedAt(s.getShippedAt())
                    .deliveredAt(s.getDeliveredAt())
                    .build();
        }
    }
}