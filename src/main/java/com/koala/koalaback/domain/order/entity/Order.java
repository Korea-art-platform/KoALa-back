package com.koala.koalaback.domain.order.entity;

import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String orderNo;

    /**
     * 주문한 회원. 비회원 주문이면 비어 있다.
     *
     * 비회원은 주문번호 + 휴대폰번호로 자기 주문을 찾는다. 나중에 같은
     * 이메일로 가입하면 그때 이 자리가 채워진다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 30)
    private String orderStatus;

    @Column(nullable = false, length = 30)
    private String paymentStatus;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal productAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal shippingAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 100)
    private String ordererName;

    @Column(nullable = false, length = 255)
    private String ordererEmail;

    @Column(nullable = false, length = 30)
    private String ordererPhone;

    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrderShipment shipment;

    @Builder
    public Order(String orderNo, User user, String currency,
                 BigDecimal productAmount, BigDecimal discountAmount,
                 BigDecimal shippingAmount, BigDecimal taxAmount,
                 BigDecimal totalAmount, String ordererName,
                 String ordererEmail, String ordererPhone) {
        this.orderNo = orderNo;
        this.user = user;
        this.orderStatus = "PENDING_PAYMENT";
        this.paymentStatus = "READY";
        this.currency = currency != null ? currency : "KRW";
        this.productAmount = productAmount;
        this.discountAmount = discountAmount;
        this.shippingAmount = shippingAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.ordererName = ordererName;
        this.ordererEmail = ordererEmail;
        this.ordererPhone = ordererPhone;
    }

    public void markPaid() {
        this.orderStatus = "PAID";
        this.paymentStatus = "PAID";
        this.paidAt = LocalDateTime.now();
    }

    public void markPreparing()  { this.orderStatus = "PREPARING"; }
    public void markShipped()    { this.orderStatus = "SHIPPED"; }
    public void markDelivered()  { this.orderStatus = "DELIVERED"; }

    public void cancel() {
        if (!isCancellable()) throw new IllegalStateException("취소 불가 상태");
        this.orderStatus = "CANCELLED";
        this.paymentStatus = "CANCELLED";
        this.cancelledAt = LocalDateTime.now();
    }

    public void forceCancel() {
        this.orderStatus  = "CANCELLED";
        this.paymentStatus = "CANCELLED";
        this.cancelledAt  = LocalDateTime.now();
    }

    public void markPaymentFailed() { this.paymentStatus = "FAILED"; }

    /** 비회원인가 */
    public boolean isGuest() { return this.user == null; }

    /**
     * 비회원으로 한 주문을 계정에 붙인다.
     *
     * 이미 주인이 있는 주문은 건드리지 않는다. 남의 주문이 옮겨 붙으면
     * 주문 내역에 모르는 주문이 나타나고, 배송지·연락처까지 보이게 된다.
     */
    public void attachTo(User owner) {
        if (this.user != null) return;
        this.user = owner;
    }

    public boolean isCancellable() {
        return "PENDING_PAYMENT".equals(orderStatus)
                || "PAID".equals(orderStatus)
                || "PREPARING".equals(orderStatus);
    }
}
