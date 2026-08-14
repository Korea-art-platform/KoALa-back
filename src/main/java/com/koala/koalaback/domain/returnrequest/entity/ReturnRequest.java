package com.koala.koalaback.domain.returnrequest.entity;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequest extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String returnNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String returnType;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String reasonDetail;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(precision = 13, scale = 2)
    private BigDecimal refundAmount;

    @Column(columnDefinition = "TEXT")
    private String adminMemo;

    private LocalDateTime processedAt;

    @Builder
    public ReturnRequest(String returnNo, Order order, User user,
                         String returnType, String reason, String reasonDetail) {
        this.returnNo     = returnNo;
        this.order        = order;
        this.user         = user;
        this.returnType   = returnType;
        this.reason       = reason;
        this.reasonDetail = reasonDetail;
        this.status       = "REQUESTED";
    }

    public void approve(BigDecimal refundAmount, String adminMemo) {
        this.status       = "APPROVED";
        this.refundAmount = refundAmount;
        this.adminMemo    = adminMemo;
        this.processedAt  = LocalDateTime.now();
    }

    public void reject(String adminMemo) {
        this.status      = "REJECTED";
        this.adminMemo   = adminMemo;
        this.processedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status      = "COMPLETED";
        this.processedAt = LocalDateTime.now();
    }
}
