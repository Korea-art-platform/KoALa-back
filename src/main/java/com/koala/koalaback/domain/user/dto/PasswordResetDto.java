package com.koala.koalaback.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

public class PasswordResetDto {
    @Getter
    public static class SendCodeRequest {
        @NotBlank @Email
        private String email;
    }

    @Getter
    public static class VerifyCodeRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8, max = 8)
        private String token;
    }

    @Getter
    public static class ResetPasswordRequest {
        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 8, max = 8)
        private String token;

        @NotBlank @Size(min = 8, max = 64)
        private String newPassword;
    }
}
