package com.koala.koalaback.domain.order.entity;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.sku.entity.Sku;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @Column(nullable = false, length = 40)
    private String skuCodeSnapshot;

    @Column(length = 40)
    private String artistCodeSnapshot;

    @Column(nullable = false, length = 200)
    private String skuNameSnapshot;

    @Column(length = 150)
    private String artistNameSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal lineTotalAmount;

    @Column(columnDefinition = "JSON")
    private String skuSnapshotJson;

    @Column(nullable = false)
    private Boolean reviewWritten;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.reviewWritten == null)  this.reviewWritten  = false;
        if (this.discountAmount == null) this.discountAmount = BigDecimal.ZERO;
        if (this.taxAmount == null)      this.taxAmount      = BigDecimal.ZERO;
    }

    @Builder
    public OrderItem(Order order, Sku sku, Artist artist,
                     String skuCodeSnapshot, String artistCodeSnapshot,
                     String skuNameSnapshot, String artistNameSnapshot,
                     Integer quantity, BigDecimal unitPrice,
                     BigDecimal discountAmount, BigDecimal taxAmount,
                     BigDecimal lineTotalAmount, String skuSnapshotJson) {
        this.order = order;
        this.sku = sku;
        this.artist = artist;
        this.skuCodeSnapshot = skuCodeSnapshot;
        this.artistCodeSnapshot = artistCodeSnapshot;
        this.skuNameSnapshot = skuNameSnapshot;
        this.artistNameSnapshot = artistNameSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.lineTotalAmount = lineTotalAmount;
        this.skuSnapshotJson = skuSnapshotJson;
        this.reviewWritten = false;
    }

    public void markReviewWritten() { this.reviewWritten = true; }
}
