package com.koala.koalaback.domain.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.entity.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class AdminDto {
    @Getter
    @Schema(name = "AdminLoginRequest")
    public static class LoginRequest {
        @NotBlank
        private String loginId;

        @NotBlank
        private String password;
    }

    @Getter
    @Schema(name = "AdminTokenResponse")
    public static class TokenResponse {
        private final String accessToken;
        private final String tokenType = "Bearer";

        public TokenResponse(String accessToken) {
            this.accessToken = accessToken;
        }
    }

    @Getter
    @Builder
    @Schema(description = "관리자 정보")
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
    @Schema(description = "재고 수동 조정. 조정 전후 수량이 감사 로그에 남는다")
    public static class StockAdjustRequest {
        @NotBlank
        @Schema(example = "A1B2C3D4E5F60718", requiredMode = Schema.RequiredMode.REQUIRED)
        private String skuCode;

        @Schema(description = "증감분. 최종 수량이 아니다 — 장부에 한 줄을 더하는 방식이라 "
                + "지금 수량을 몰라도 적을 수 있다. 음수면 차감", example = "3")
        private int delta;

        @Size(max = 200)
        @Schema(description = "사유. 감사 로그에 남는다", example = "재고 실사 반영")
        private String memo;
    }

    @Getter
    @Builder
    @Schema(description = "관리자 권한")
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
