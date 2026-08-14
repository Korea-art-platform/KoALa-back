package com.koala.koalaback.domain.user.service;

import com.koala.koalaback.domain.user.dto.PasswordResetDto;
import com.koala.koalaback.domain.user.entity.PasswordResetToken;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.PasswordResetTokenRepository;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.infra.mail.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void sendResetCode(PasswordResetDto.SendCodeRequest req) {
        userRepository.findByEmail(req.getEmail()).ifPresent(user -> {
            if (user.getOauthProvider() != null) return;

            tokenRepository.deleteAllByEmail(req.getEmail());
            String token = generateCode();
            tokenRepository.save(
                    PasswordResetToken.builder()
                            .email(req.getEmail())
                            .token(token)
                            .build()
            );
            emailService.sendPasswordResetEmail(req.getEmail(), token);
        });
    }

    @Transactional
    public void verifyCode(PasswordResetDto.VerifyCodeRequest req) {
        PasswordResetToken resetToken = tokenRepository
                .findTopByEmailAndTokenAndIsUsedFalseAndIsVerifiedFalseOrderByCreatedAtDesc(
                        req.getEmail(), req.getToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (resetToken.isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        resetToken.verify();
    }

    @Transactional
    public void resetPassword(PasswordResetDto.ResetPasswordRequest req) {
        PasswordResetToken resetToken = tokenRepository
                .findTopByEmailAndTokenAndIsUsedFalseAndIsVerifiedTrueOrderByCreatedAtDesc(
                        req.getEmail(), req.getToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (resetToken.isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updatePassword(passwordEncoder.encode(req.getNewPassword()));
        resetToken.use();
        tokenRepository.deleteAllByEmail(req.getEmail());
    }

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TOKEN_LENGTH = 8;

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
