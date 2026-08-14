package com.koala.koalaback.domain.user.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import com.koala.koalaback.global.crypto.AesGcmCryptoConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAddress extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String label;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String recipientName;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String recipientPhone;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String zipCode;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 1024)
    private String address1;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(length = 1024)
    private String address2;

    @Column(nullable = false)
    private Boolean isDefault;

    @Builder
    public UserAddress(User user, String label, String recipientName,
                       String recipientPhone, String zipCode,
                       String address1, String address2, Boolean isDefault) {
        this.user = user;
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = isDefault != null ? isDefault : false;
    }

    public void update(String label, String recipientName, String recipientPhone,
                       String zipCode, String address1, String address2, Boolean isDefault) {
        this.label = label;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
        if (isDefault != null) {
            this.isDefault = isDefault;
        }
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
