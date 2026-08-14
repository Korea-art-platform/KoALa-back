package com.koala.koalaback.domain.sku.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sku_stock_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuStockLedger {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @Column(nullable = false)
    private Integer delta;

    @Column(nullable = false, length = 30)
    private String reason;

    @Column(length = 30)
    private String refType;

    private Long refId;

    @Column(length = 200)
    private String memo;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @Builder
    public SkuStockLedger(Sku sku, Integer delta, String reason,
                          String refType, Long refId, String memo) {
        this.sku = sku;
        this.delta = delta;
        this.reason = reason;
        this.refType = refType;
        this.refId = refId;
        this.memo = memo;
    }
}
