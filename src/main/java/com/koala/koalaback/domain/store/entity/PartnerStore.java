package com.koala.koalaback.domain.store.entity;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.global.common.BaseTimeEntity;
import com.koala.koalaback.global.crypto.AesGcmCryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "partner_stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerStore extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String storeCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(length = 200)
    private String zipCode;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 1024)
    private String address;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(length = 1024)
    private String addressDetail;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String phone;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(length = 512)
    private String phone2;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(length = 512)
    private String email;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 700)
    private String mapUrl;

    @Column(length = 700)
    private String snsUrl;

    @Column(length = 700)
    private String imageUrl;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private Admin createdByAdmin;

    private LocalDateTime deletedAt;

    @Builder
    public PartnerStore(String storeCode, String name, String zipCode, String address,
                        String addressDetail, String phone, String phone2, String email,
                        String description, String mapUrl, String snsUrl, String imageUrl,
                        Integer sortOrder, Admin createdByAdmin) {
        this.storeCode = storeCode;
        this.name = name;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.phone = phone;
        this.phone2 = phone2;
        this.email = email;
        this.description = description;
        this.mapUrl = mapUrl;
        this.snsUrl = snsUrl;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = true;
        this.createdByAdmin = createdByAdmin;
    }

    public void update(String name, String zipCode, String address, String addressDetail,
                       String phone, String phone2, String email, String description,
                       String mapUrl, String snsUrl, String imageUrl, Integer sortOrder) {
        this.name = name;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.phone = phone;
        this.phone2 = phone2;
        this.email = email;
        this.description = description;
        this.mapUrl = mapUrl;
        this.snsUrl = snsUrl;
        this.imageUrl = imageUrl;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }

    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }
}
