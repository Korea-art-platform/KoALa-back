package com.koala.koalaback.domain.admin.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String adminCode;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Integer loginFailCount;

    private LocalDateTime lastLoginAt;

    @Column(length = 45)
    private String lastLoginIp;

    private LocalDateTime passwordChangedAt;
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AdminRoleMapping> roleMappings = new ArrayList<>();

    @Builder
    public Admin(String adminCode, String loginId, String passwordHash,
                 String name, String email, String phone) {
        this.adminCode = adminCode;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.status = "ACTIVE";
        this.loginFailCount = 0;
    }

    public void updateLastLogin(String ip) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ip;
        this.loginFailCount = 0;
    }

    public void incrementLoginFail() {
        this.loginFailCount++;
        if (this.loginFailCount >= 5) this.status = "LOCKED";
    }

    public void unlock() {
        this.status = "ACTIVE";
        this.loginFailCount = 0;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.status = "INACTIVE";
    }

    public boolean isActive() {
        return "ACTIVE".equals(this.status) && this.deletedAt == null;
    }
}
