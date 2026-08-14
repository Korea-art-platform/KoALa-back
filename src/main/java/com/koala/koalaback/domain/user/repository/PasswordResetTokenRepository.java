package com.koala.koalaback.domain.user.repository;

import com.koala.koalaback.domain.user.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findTopByEmailAndTokenAndIsUsedFalseAndIsVerifiedFalseOrderByCreatedAtDesc(
            String email, String token
    );

    Optional<PasswordResetToken> findTopByEmailAndTokenAndIsUsedFalseAndIsVerifiedTrueOrderByCreatedAtDesc(
            String email, String token
    );

    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.email = :email")
    void deleteAllByEmail(String email);
}
