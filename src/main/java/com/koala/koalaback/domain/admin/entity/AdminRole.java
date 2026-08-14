package com.koala.koalaback.domain.admin.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRole extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String roleCode;

    @Column(nullable = false, length = 100)
    private String roleName;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private Boolean isActive;

    @Builder
    public AdminRole(String roleCode, String roleName, String description) {
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.isActive = true;
    }

    public void deactivate() { this.isActive = false; }
    public void activate()   { this.isActive = true; }
}
