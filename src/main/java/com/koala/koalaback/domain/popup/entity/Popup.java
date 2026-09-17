package com.koala.koalaback.domain.popup.entity;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "popups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup extends BaseTimeEntity {
    public static final String LANG_KO = "ko";
    public static final String LANG_EN = "en";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_TEMPLATE = "TEMPLATE";
    public static final String PLACEMENT_HOME = "HOME";
    public static final String PLACEMENT_ALL = "ALL";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String popupCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Boolean showDismiss;

    @Column(nullable = false, length = 2)
    private String language;

    @Column(nullable = false, length = 20)
    private String displayType;

    @Column(length = 1024)
    private String imageUrl;

    @Column(length = 2000)
    private String body;

    @Column(nullable = false)
    private Boolean showLinkButton;

    @Column(nullable = false, length = 20)
    private String placement;

    @Column(length = 1024)
    private String landingUrl;

    @Column(nullable = false)
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private Admin createdByAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_admin_id")
    private Admin updatedByAdmin;

    private LocalDateTime deletedAt;

    @Builder
    public Popup(String popupCode, String title, Boolean isActive, Boolean showDismiss,
                 String language, String displayType, String imageUrl, String body,
                 Boolean showLinkButton, String placement, String landingUrl,
                 Integer sortOrder, Admin createdByAdmin) {
        this.popupCode = popupCode;
        this.title = title;
        this.isActive = isActive != null ? isActive : false;
        this.showDismiss = showDismiss != null ? showDismiss : true;
        this.language = language;
        this.displayType = displayType;
        this.imageUrl = imageUrl;
        this.body = body;
        this.showLinkButton = showLinkButton != null ? showLinkButton : false;
        this.placement = placement;
        this.landingUrl = landingUrl;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.createdByAdmin = createdByAdmin;
    }

    public void update(String title, Boolean isActive, Boolean showDismiss,
                       String language, String displayType, String imageUrl, String body,
                       Boolean showLinkButton, String placement, String landingUrl,
                       Integer sortOrder, Admin updatedByAdmin) {
        this.title = title;
        if (isActive != null) this.isActive = isActive;
        if (showDismiss != null) this.showDismiss = showDismiss;
        this.language = language;
        this.displayType = displayType;
        this.imageUrl = imageUrl;
        this.body = body;
        if (showLinkButton != null) this.showLinkButton = showLinkButton;
        this.placement = placement;
        this.landingUrl = landingUrl;
        if (sortOrder != null) this.sortOrder = sortOrder;
        this.updatedByAdmin = updatedByAdmin;
    }

    public void activate()   { this.isActive = true; }
    public void deactivate() { this.isActive = false; }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.isActive = false;
    }
}
