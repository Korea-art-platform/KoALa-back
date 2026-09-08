package com.koala.koalaback.domain.order.dto;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(name = "OrderCreateRequest", description = "주문 생성 요청. cartItemIds 와 directItem 중 하나를 쓴다")
    public static class CreateRequest {
        @NotBlank
        @Schema(description = "주문자 이름", example = "김주문", requiredMode = Schema.RequiredMode.REQUIRED)
        private String ordererName;

        @NotBlank
        @Schema(description = "주문자 이메일. 나중에 같은 이메일로 가입하면 비회원 주문이 그 계정에 붙는다",
                example = "buyer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String ordererEmail;

        @NotBlank
        @Schema(description = "주문자 휴대폰번호. 비회원 주문 조회에 이 번호를 쓴다. 저장할 때 형식을 맞춘다",
                example = "010-0000-0000", requiredMode = Schema.RequiredMode.REQUIRED)
        private String ordererPhone;

        @NotNull @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private ShipmentRequest shipment;

        /** 담아 둔 것 중 고른 것만 주문할 때 쓴다. 비우면 담은 것 전부. */
        @Schema(description = "주문할 장바구니 항목 id. 비우면 담긴 것 전부. directItem 이 있으면 무시된다",
                example = "[12, 13]")
        private List<Long> cartItemIds;

        /**
         * 장바구니를 거치지 않고 한 건만 살 때 쓴다.
         *
         * 이게 있으면 cartItemIds 는 보지 않는다. 상품 화면의 "구매하기"가
         * 장바구니에 담고 넘어가던 탓에, 담아 둔 다른 물건까지 같이 결제됐다.
         */
        @Schema(description = "장바구니를 거치지 않고 한 건만 살 때. 이게 있으면 cartItemIds 는 보지 않는다. "
                + "비회원 주문은 이 값이 반드시 있어야 한다")
        private DirectItemRequest directItem;
    }

    @Getter
    @Schema(description = "비회원 주문 조회 요청. 둘 다 맞아야 열린다")
    public static class GuestLookupRequest {
        @NotBlank
        @Schema(description = "주문번호", example = "KL-20260907143000-A1B2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String orderNo;

        @NotBlank
        @Schema(description = "주문할 때 적은 휴대폰번호", example = "010-0000-0000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String phone;
    }

    @Getter
    @Schema(description = "바로 구매할 한 건. 가격은 이 요청이 아니라 DB 에서 읽는다")
    public static class DirectItemRequest {
        @NotBlank
        @Schema(description = "상품 코드", example = "A1B2C3D4E5F60718",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String skuCode;

        /** 비우면 1개로 본다. */
        @Schema(description = "수량. 비우면 1", example = "1", defaultValue = "1")
        private Integer quantity;
    }

    @Getter
    @Schema(description = "배송지")
    public static class ShipmentRequest {
        @NotBlank
        @Schema(description = "받는 사람", example = "김수령", requiredMode = Schema.RequiredMode.REQUIRED)
        private String recipientName;

        @NotBlank
        @Schema(description = "받는 사람 연락처", example = "010-0000-0000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String recipientPhone;

        @NotBlank
        @Schema(description = "우편번호", example = "00000", requiredMode = Schema.RequiredMode.REQUIRED)
        private String zipCode;

        @NotBlank
        @Schema(description = "기본 주소", example = "서울시 ○○구 ○○로 00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String address1;

        @Schema(description = "상세 주소", example = "000동 000호")
        private String address2;

        @Schema(description = "배송 요청사항", example = "부재 시 경비실에 맡겨주세요")
        private String deliveryRequest;
    }

    @Getter
    @Schema(description = "관리자 주문 강제취소 요청")
    public static class AdminCancelRequest {
        @NotBlank
        @Schema(description = "취소 사유. 기록에 남는다", example = "고객 요청",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String reason;

        @Positive
        @Schema(description = "환불 금액. 비우면 승인 금액 전액. 승인 금액을 넘을 수 없다", example = "33000")
        private BigDecimal cancelAmount;
    }

    @Getter
    @Schema(description = "송장 등록 요청")
    public static class RegisterTrackingRequest {
        @NotBlank
        @Schema(description = "택배사 코드. 목록은 GET /admin/api/v1/orders/carriers",
                example = "CJ", requiredMode = Schema.RequiredMode.REQUIRED)
        private String carrierCode;

        @NotBlank
        @Schema(description = "송장번호", example = "000000000000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String trackingNo;
    }

    @Getter
    @Builder
    @Schema(description = "주문 목록에 쓰는 요약. 목록 화면에 필요한 것만 담는다")
    public static class OrderSummaryResponse {
        private Long id;

        @Schema(description = "회원 주문이면 회원 id, 비회원 주문이면 null")
        private Long userId;

        @Schema(description = "주문번호", example = "KL-20260907143000-A1B2")
        private String orderNo;

        @Schema(description = "주문 상태. PENDING_PAYMENT(결제 대기) · PAID(결제 완료) · PREPARING(준비중) · SHIPPED(배송중) · DELIVERED(배송완료) · CANCELLED(취소)", example = "PAID")
        private String orderStatus;

        @Schema(description = "결제 상태. READY(대기) · PAID(완료) · CANCELLED(취소) · FAILED(실패)", example = "PAID")
        private String paymentStatus;

        @Schema(description = "총 결제 금액. 부가세와 배송비가 포함된 값이다", example = "366000")
        private BigDecimal totalAmount;

        @Schema(description = "주문 항목 수", example = "2")
        private int itemCount;

        @Schema(description = "첫 항목의 상품명. 목록에 '○○ 외 1건' 으로 쓴다")
        private String firstSkuName;

        @Schema(description = "첫 항목의 대표 이미지. 상품이 지워졌으면 null")
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
    @Schema(description = "주문 상세")
    public static class OrderDetailResponse {
        private Long id;

        @Schema(description = "주문번호", example = "KL-20260907143000-A1B2")
        private String orderNo;

        @Schema(description = "주문 상태. PENDING_PAYMENT(결제 대기) · PAID(결제 완료) · PREPARING(준비중) · SHIPPED(배송중) · DELIVERED(배송완료) · CANCELLED(취소)", example = "PAID")
        private String orderStatus;

        @Schema(description = "결제 상태. READY(대기) · PAID(완료) · CANCELLED(취소) · FAILED(실패)", example = "PAID")
        private String paymentStatus;

        @Schema(description = "통화. 지정하지 않으면 KRW", example = "KRW")
        private String currency;

        @Schema(description = "상품 공급가액 합계. 부가세를 뺀 금액이다", example = "330000")
        private BigDecimal productAmount;

        @Schema(description = "할인 금액", example = "0")
        private BigDecimal discountAmount;

        @Schema(description = "배송비. 상품 금액(부가세 포함)이 5만원 이상이면 0, 아니면 3000", example = "3000")
        private BigDecimal shippingAmount;

        @Schema(description = "부가세 합계. 상품 부가세와 배송비에 든 세액을 더한 값이다. "
                + "원작처럼 면세 분류에는 상품 부가세가 붙지 않는다", example = "33272")
        private BigDecimal taxAmount;

        @Schema(description = "실제 청구 금액. 상품(부가세 포함) + 배송비", example = "366000")
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
    @Schema(description = "주문 항목. 상품 코드·이름·작가명은 주문 시점의 값을 그대로 남긴 것이라 "
            + "나중에 상품이 바뀌거나 지워져도 이 주문에서는 변하지 않는다")
    public static class OrderItemResponse {
        private Long id;

        @Schema(description = "주문 시점의 상품 코드", example = "A1B2C3D4E5F60718")
        private String skuCode;

        @Schema(description = "주문 시점의 상품명")
        private String skuName;

        @Schema(description = "주문 시점의 작가명")
        private String artistName;

        @Schema(description = "지금 상품의 대표 이미지. 상품이 지워졌으면 null")
        private String primaryImageUrl;

        @Schema(example = "1")
        private Integer quantity;

        @Schema(description = "주문 시점의 단가", example = "165000")
        private BigDecimal unitPrice;

        @Schema(description = "단가 × 수량", example = "165000")
        private BigDecimal lineTotalAmount;

        @Schema(description = "이 항목에 리뷰를 썼는지. 주문 화면이 리뷰 쓰기를 다시 권할지 정한다",
                example = "false")
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
    @Schema(description = "배송 정보")
    public static class ShipmentResponse {
        private String recipientName;
        private String recipientPhone;
        private String zipCode;
        private String address1;
        private String address2;
        private String deliveryRequest;

        @Schema(description = "택배사 코드. 송장 등록 전에는 null", example = "CJ")
        private String carrierCode;

        @Schema(description = "송장번호. 송장 등록 전에는 null")
        private String trackingNo;

        @Schema(description = "송장이 등록된 시각")
        private LocalDateTime shippedAt;

        @Schema(description = "배송완료로 표시된 시각. 이 값이 있어야 반품·교환을 신청할 수 있다")
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
