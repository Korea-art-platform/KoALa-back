package com.koala.koalaback.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @Column(nullable = false, length = 50)
    private String actionType;

    @Column(nullable = false, length = 50)
    private String targetType;

    private Long targetId;

    @Column(length = 255)
    private String requestPath;

    @Column(length = 10)
    private String httpMethod;

    @Column(length = 45)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(columnDefinition = "JSON")
    private String beforeData;

    @Column(columnDefinition = "JSON")
    private String afterData;

    @Column(length = 500)
    private String memo;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    @Builder
    public AdminAuditLog(Admin admin, String actionType, String targetType,
                         Long targetId, String requestPath, String httpMethod,
                         String ipAddress, String userAgent,
                         String beforeData, String afterData, String memo) {
        this.admin = admin;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestPath = requestPath;
        this.httpMethod = httpMethod;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.memo = memo;
    }
}
