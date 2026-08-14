package com.koala.koalaback.domain.review.entity;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sku_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuReview extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String reviewCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false, length = 20)
    private String reviewStatus;

    @Column(nullable = false)
    private Boolean isVisible;

    @Column(nullable = false)
    private Boolean isFeatured;

    @Column(nullable = false)
    private Integer likeCount;

    @Column(nullable = false)
    private Integer reportCount;

    @Column(length = 500)
    private String adminMemo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by_admin_id")
    private Admin moderatedByAdmin;

    private LocalDateTime moderatedAt;
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<SkuReviewMedia> mediaList = new ArrayList<>();

    @Builder
    public SkuReview(String reviewCode, OrderItem orderItem, Order order,
                     Sku sku, User user, Integer rating,
                     String title, String content) {
        this.reviewCode = reviewCode;
        this.orderItem = orderItem;
        this.order = order;
        this.sku = sku;
        this.user = user;
        this.rating = rating;
        this.title = title;
        this.content = content;
        this.reviewStatus = "PENDING";
        this.isVisible = false;
        this.isFeatured = false;
        this.likeCount = 0;
        this.reportCount = 0;
    }

    public void approve(Admin admin) {
        this.reviewStatus = "APPROVED";
        this.isVisible = true;
        this.moderatedByAdmin = admin;
        this.moderatedAt = LocalDateTime.now();
    }

    public void hide(Admin admin, String memo) {
        this.reviewStatus = "HIDDEN";
        this.isVisible = false;
        this.adminMemo = memo;
        this.moderatedByAdmin = admin;
        this.moderatedAt = LocalDateTime.now();
    }

    public void reject(Admin admin, String memo) {
        this.reviewStatus = "REJECTED";
        this.isVisible = false;
        this.adminMemo = memo;
        this.moderatedByAdmin = admin;
        this.moderatedAt = LocalDateTime.now();
    }

    public void updateContent(int rating, String title, String content) {
        this.rating = rating;
        this.title = title;
        this.content = content;
    }

    public void setFeatured(boolean featured) { this.isFeatured = featured; }
    public void incrementLike()               { this.likeCount++; }
    public void incrementReport()             { this.reportCount++; }
    public void softDelete()                  { this.deletedAt = LocalDateTime.now(); }
    public boolean isApproved()               { return "APPROVED".equals(this.reviewStatus); }
}
