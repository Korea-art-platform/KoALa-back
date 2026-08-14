package com.koala.koalaback.domain.user.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import com.koala.koalaback.global.crypto.AesGcmCryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "addresses")
public class User extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String userCode;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(nullable = false, length = 512)
    private String name;

    @Convert(converter = AesGcmCryptoConverter.class)
    @Column(length = 512)
    private String phone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 20)
    private String oauthProvider;

    @Column(length = 100)
    private String oauthId;

    private LocalDateTime lastLoginAt;
    private LocalDateTime deletedAt;

    @Column(name = "toss_disconnected_at")
    private LocalDateTime tossDisconnectedAt;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserAddress> addresses = new ArrayList<>();

    @Builder
    public User(String userCode, String email, String passwordHash,
                String name, String phone) {
        this.userCode = userCode;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.status = "ACTIVE";
    }

    public static User createOAuthUser(String userCode, String email, String name, String oauthProvider, String oauthId) {
        User user = new User();
        user.userCode = userCode;
        user.email = email;
        user.passwordHash = "";
        user.name = name;
        user.oauthProvider = oauthProvider;
        user.oauthId = oauthId;
        user.status = "ACTIVE";
        return user;
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void updateOAuthInfo(String name) {
        this.name = name;
    }

    public void linkOAuth(String provider, String oauthId) {
        this.oauthProvider = provider;
        this.oauthId = oauthId;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.status = "INACTIVE";
    }

    public void disconnectToss() {
        this.tossDisconnectedAt = LocalDateTime.now();
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public void suspend()  { this.status = "SUSPENDED"; }
    public void activate() { this.status = "ACTIVE"; }

    public boolean isActive() {
        return "ACTIVE".equals(this.status) && this.deletedAt == null;
    }
}
