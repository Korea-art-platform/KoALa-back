package com.koala.koalaback.domain.inquiry.entity;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String inquiryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private Boolean isSecret = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by_id")
    private Admin answeredBy;

    private LocalDateTime answeredAt;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answerContent;

    private LocalDateTime deletedAt;

    @Builder
    public Inquiry(String inquiryCode, User user, Order order,
                   String category, String title, String content, Boolean isSecret) {
        this.inquiryCode = inquiryCode;
        this.user        = user;
        this.order       = order;
        this.category    = category;
        this.title       = title;
        this.content     = content;
        this.isSecret    = isSecret != null && isSecret;
        this.status      = "PENDING";
    }

    public void answer(Admin admin, String answerContent) {
        this.answeredBy      = admin;
        this.answerContent   = answerContent;
        this.answeredAt      = LocalDateTime.now();
        this.status          = "ANSWERED";
    }

    public void close() {
        this.status = "CLOSED";
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
