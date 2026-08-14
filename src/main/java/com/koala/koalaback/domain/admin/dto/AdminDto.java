package com.koala.koalaback.domain.admin.dto;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.entity.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class AdminDto {
    @Getter
    public static class LoginRequest {
        @NotBlank
        private String loginId;

        @NotBlank
        private String password;
    }

    @Getter
    public static class TokenResponse {
        private final String accessToken;
        private final String tokenType = "Bearer";

        public TokenResponse(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    @Getter
    @Builder
    public static class AdminResponse {
        private Long id;
        private String adminCode;
        private String loginId;
        private String name;
        private String email;
        private String status;
        private List<String> roles;

        public static AdminResponse from(Admin a) {
            return AdminResponse.builder()
                    .id(a.getId())
                    .adminCode(a.getAdminCode())
                    .loginId(a.getLoginId())
                    .name(a.getName())
                    .email(a.getEmail())
                    .status(a.getStatus())
                    .roles(a.getRoleMappings().stream()
                            .map(m -> m.getRole().getRoleCode())
                            .toList())
                    .build();
        }
    }

    @Getter
    public static class StockAdjustRequest {
        @NotBlank
        private String skuCode;

        private int delta;

        @Size(max = 200)
        private String memo;
    }

    @Getter
    @Builder
    public static class RoleResponse {
        private Long id;
        private String roleCode;
        private String roleName;
        private String description;
        private Boolean isActive;

        public static RoleResponse from(AdminRole r) {
            return RoleResponse.builder()
                    .id(r.getId())
                    .roleCode(r.getRoleCode())
                    .roleName(r.getRoleName())
                    .description(r.getDescription())
                    .isActive(r.getIsActive())
                    .build();
        }
    }
}
