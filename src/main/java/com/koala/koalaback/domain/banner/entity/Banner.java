package com.koala.koalaback.domain.banner.entity;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String bannerCode;

    @Column(nullable = false, length = 30)
    private String bannerType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 255)
    private String subtitle;

    @Column(length = 100)
    private String badge;

    @Column(length = 500)
    private String description;

    @Column(length = 700)
    private String imageUrl;

    @Column(length = 700)
    private String mobileImageUrl;

    @Column(length = 700)
    private String videoUrl;

    @Column(length = 700)
    private String linkUrl;

    @Column(nullable = false, length = 20)
    private String linkTarget;

    @Column(length = 30)
    private String bgColor;

    @Column(length = 30)
    private String textColor;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean isActive;

    private LocalDateTime visibleFrom;
    private LocalDateTime visibleTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private Admin createdByAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_admin_id")
    private Admin updatedByAdmin;

    private LocalDateTime deletedAt;

    @Builder
    public Banner(String bannerCode, String bannerType, String title, String subtitle,
                  String badge, String description,
                  String imageUrl, String mobileImageUrl, String videoUrl,
                  String linkUrl, String linkTarget,
                  String bgColor, String textColor, Integer sortOrder,
                  LocalDateTime visibleFrom, LocalDateTime visibleTo, Admin createdByAdmin) {
        this.bannerCode = bannerCode;
        this.bannerType = bannerType;
        this.title = title;
        this.subtitle = subtitle;
        this.badge = badge;
        this.description = description;
        this.imageUrl = imageUrl;
        this.mobileImageUrl = mobileImageUrl;
        this.videoUrl = videoUrl;
        this.linkUrl = linkUrl;
        this.linkTarget = linkTarget != null ? linkTarget : "SELF";
        this.bgColor = bgColor;
        this.textColor = textColor;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isActive = true;
        this.visibleFrom = visibleFrom;
        this.visibleTo = visibleTo;
        this.createdByAdmin = createdByAdmin;
    }

    public void update(String title, String subtitle, String badge, String description,
                       String imageUrl, String mobileImageUrl, String videoUrl,
                       String linkUrl, String linkTarget,
                       String bgColor, String textColor, Integer sortOrder,
                       LocalDateTime visibleFrom, LocalDateTime visibleTo,
                       Admin updatedByAdmin) {
        this.title = title;
        this.subtitle = subtitle;
        this.badge = badge;
        this.description = description;
        this.imageUrl = imageUrl;
        this.mobileImageUrl = mobileImageUrl;
        this.videoUrl = videoUrl;
        this.linkUrl = linkUrl;
        this.linkTarget = linkTarget;
        this.bgColor = bgColor;
        this.textColor = textColor;
        this.sortOrder = sortOrder;
        this.visibleFrom = visibleFrom;
        this.visibleTo = visibleTo;
        this.updatedByAdmin = updatedByAdmin;
    }

    public void updateImageUrl(String url) { this.imageUrl = url; }

    public void updateVideoUrl(String url) { this.videoUrl = url; }

    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }

    public boolean isVisible() {
        if (!isActive || deletedAt != null) return false;
        LocalDateTime now = LocalDateTime.now();
        if (visibleFrom != null && now.isBefore(visibleFrom)) return false;
        if (visibleTo   != null && now.isAfter(visibleTo))    return false;
        return true;
    }
}
