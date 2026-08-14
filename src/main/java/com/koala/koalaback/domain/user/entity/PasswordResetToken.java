package com.koala.koalaback.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="password_reset_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String email;
    @Column(nullable = false, length = 64)
    private String token;
    @Column(nullable = false)
    private boolean isUsed;

    @Column(nullable = false)
    private boolean isVerified;

    @Column(nullable = false)
    private LocalDateTime expiredAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PasswordResetToken(String email, String token) {
        this.email = email;
        this.token = token;
        this.isUsed = false;
        this.isVerified = false;
        this.expiredAt = LocalDateTime.now().plusMinutes(5);
        this.createdAt = LocalDateTime.now();
    }
    public boolean isExpired(){
        return LocalDateTime.now().isAfter(this.expiredAt);
    }
    public void verify() {
        this.isVerified = true;
    }
    public void use(){
        this.isUsed = true;
    }
}
