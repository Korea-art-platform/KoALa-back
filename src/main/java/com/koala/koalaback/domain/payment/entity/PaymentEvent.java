package com.koala.koalaback.domain.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, length = 30)
    private String eventType;

    @Column(nullable = false, length = 20)
    private String eventStatus;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String providerEventId;

    @Column(columnDefinition = "JSON")
    private String payloadJson;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @Builder
    public PaymentEvent(Payment payment, String eventType, String eventStatus,
                        BigDecimal amount, String providerEventId, String payloadJson) {
        this.payment = payment;
        this.eventType = eventType;
        this.eventStatus = eventStatus;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.providerEventId = providerEventId;
        this.payloadJson = payloadJson;
    }
}
