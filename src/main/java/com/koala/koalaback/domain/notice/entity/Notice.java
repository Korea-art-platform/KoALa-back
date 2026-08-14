package com.koala.koalaback.domain.notice.entity;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String noticeCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Boolean isPinned = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private Admin createdByAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_admin_id")
    private Admin updatedByAdmin;

    private LocalDateTime deletedAt;

    @Builder
    public Notice(String noticeCode, String title, String content,
                  Boolean isPinned, Admin createdByAdmin) {
        this.noticeCode = noticeCode;
        this.title = title;
        this.content = content;
        this.isPinned = isPinned != null && isPinned;
        this.isActive = true;
        this.createdByAdmin = createdByAdmin;
    }

    public void update(String title, String content, Boolean isPinned, Admin updatedByAdmin) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned != null && isPinned;
        this.updatedByAdmin = updatedByAdmin;
    }

    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }
}
