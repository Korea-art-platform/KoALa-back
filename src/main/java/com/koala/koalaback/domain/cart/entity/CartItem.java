package com.koala.koalaback.domain.cart.entity;

import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal unitPrice;

    @Column(columnDefinition = "JSON")
    private String optionSnapshotJson;

    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() { this.addedAt = LocalDateTime.now(); }

    @Builder
    public CartItem(Cart cart, Sku sku, Integer quantity,
                    BigDecimal unitPrice, String optionSnapshotJson) {
        this.cart = cart;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.optionSnapshotJson = optionSnapshotJson;
    }

    public void updateQuantity(int quantity) { this.quantity = quantity; }
    public void updatePrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
