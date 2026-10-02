package com.koala.koalaback.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "onsite_payment_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnSitePaymentSetting {
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(nullable = false)
    private Integer version;

    private LocalDateTime updatedAt;

    public static OnSitePaymentSetting initial() {
        OnSitePaymentSetting s = new OnSitePaymentSetting();
        s.id = SINGLETON_ID;
        s.enabled = false;
        s.version = 0;
        return s;
    }

    public boolean isOn() {
        return Boolean.TRUE.equals(enabled) && pinHash != null;
    }

    public boolean hasPin() {
        return pinHash != null;
    }

    public void changePin(String newPinHash) {
        this.pinHash = newPinHash;
        bump();
    }

    public void enable() {
        if (Boolean.TRUE.equals(enabled)) return;
        this.enabled = true;
        bump();
    }

    public void disable() {
        if (!Boolean.TRUE.equals(enabled)) return;
        this.enabled = false;
        bump();
    }

    private void bump() {
        this.version = version + 1;
        this.updatedAt = LocalDateTime.now();
    }
}
