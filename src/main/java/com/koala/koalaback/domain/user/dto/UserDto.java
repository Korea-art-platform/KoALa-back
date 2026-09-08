package com.koala.koalaback.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.entity.UserAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class UserDto {
    @Getter
    @Schema(description = "회원가입 요청. 가입과 동시에 장바구니가 만들어지고, "
            + "같은 이메일로 한 비회원 주문이 이 계정에 붙는다")
    public static class SignupRequest {
        @NotBlank @Email
        @Schema(description = "이메일. 로그인 아이디이자 비회원 주문을 잇는 열쇠다",
                example = "buyer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[\\d\\W]).{8,64}$",
            message = "비밀번호는 8자 이상이며 영문자와 숫자/특수문자를 각각 1자 이상 포함해야 합니다."
        )
        @Schema(description = "8~64자. 영문자 1자 이상과 숫자 또는 특수문자 1자 이상을 포함해야 한다", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;

        @NotBlank @Size(max = 100)
        @Schema(description = "이름", example = "김코알라", requiredMode = Schema.RequiredMode.REQUIRED)
        private String name;

        @Schema(description = "휴대폰번호. 저장할 때 E.164 형식으로 맞춘다", example = "010-0000-0000")
        private String phone;
    }

    @Getter
    @Schema(name = "UserLoginRequest",
            description = "로그인 요청. 없는 이메일과 틀린 비밀번호는 같은 오류로 답한다")
    public static class LoginRequest {
        @NotBlank @Email
        @Schema(example = "buyer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;

        @NotBlank
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;
    }

    @Getter
    @Schema(description = "내 정보 수정. 보낸 항목만 바뀌고 비운 항목은 기존 값을 그대로 둔다")
    public static class UpdateProfileRequest {
        @Size(max = 100)
        @Schema(description = "이름. 비우면 그대로", example = "김코알라")
        private String name;

        @Size(max = 30)
        @Schema(description = "휴대폰번호. 비우면 그대로. 저장할 때 E.164 형식으로 맞춘다",
                example = "010-0000-0000")
        private String phone;
    }

    @Getter
    @Schema(description = "비밀번호 변경. 로그인한 상태여도 현재 비밀번호를 다시 확인한다")
    public static class ChangePasswordRequest {
        @NotBlank
        @Schema(description = "현재 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[\\d\\W]).{8,64}$",
            message = "비밀번호는 8자 이상이며 영문자와 숫자/특수문자를 각각 1자 이상 포함해야 합니다."
        )
        @Schema(description = "새 비밀번호. 8~64자. 영문자 1자 이상과 숫자 또는 특수문자 1자 이상을 포함해야 한다", requiredMode = Schema.RequiredMode.REQUIRED)
        private String newPassword;
    }

    @Getter
    @Schema(description = "갱신 토큰 요청 본문. 실제 갱신 API 는 본문이 아니라 쿠키에서 읽는다")
    public static class RefreshRequest {
        @NotBlank
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String refreshToken;
    }

    @Getter
    @Builder
    @Schema(name = "UserTokenResponse",
            description = "발급된 토큰. 같은 값이 httpOnly 쿠키로도 내려간다 — "
                    + "접근 토큰은 전 경로, 갱신 토큰은 /api/v1/auth 아래에서만 실린다")
    public static class TokenResponse {
        @Schema(description = "접근 토큰")
        private String accessToken;

        @Schema(description = "갱신 토큰. 서버에도 저장되며 로그아웃하면 지워진다")
        private String refreshToken;

        @Schema(example = "Bearer")
        private String tokenType;

        public static TokenResponse of(String accessToken, String refreshToken) {
            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .build();
        }
    }

    @Getter
    @Builder
    @Schema(description = "내 정보")
    public static class ProfileResponse {
        private Long id;

        @Schema(description = "회원 코드")
        private String userCode;

        @Schema(example = "buyer@example.com")
        private String email;

        private String name;

        @Schema(description = "E.164 형식으로 저장된 휴대폰번호")
        private String phone;

        @Schema(description = "상태. ACTIVE(정상) · SUSPENDED(정지) · INACTIVE(휴면)",
                example = "ACTIVE")
        private String status;

        @Schema(description = "소셜 로그인으로 가입했으면 그 제공자(KAKAO · NAVER). "
                + "이메일·비밀번호로 가입했으면 null. 이 값이 있으면 비밀번호 재설정 메일을 보내지 않는다",
                example = "KAKAO")
        private String oauthProvider;

        private LocalDateTime createdAt;

        public static ProfileResponse from(User user) {
            return ProfileResponse.builder()
                    .id(user.getId())
                    .userCode(user.getUserCode())
                    .email(user.getEmail())
                    .name(user.getName())
                    .phone(user.getPhone())
                    .status(user.getStatus())
                    .oauthProvider(user.getOauthProvider())
                    .createdAt(user.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Schema(description = "배송지 등록. 첫 배송지는 요청과 무관하게 기본이 된다")
    public static class AddressCreateRequest {
        @Schema(description = "배송지 이름", example = "집")
        private String label;

        @NotBlank
        @Schema(example = "김수령", requiredMode = Schema.RequiredMode.REQUIRED)
        private String recipientName;

        @NotBlank
        @Schema(example = "010-0000-0000", requiredMode = Schema.RequiredMode.REQUIRED)
        private String recipientPhone;

        @NotBlank
        @Schema(example = "00000", requiredMode = Schema.RequiredMode.REQUIRED)
        private String zipCode;

        @NotBlank
        @Schema(example = "서울시 ○○구 ○○로 00", requiredMode = Schema.RequiredMode.REQUIRED)
        private String address1;

        @Schema(example = "000동 000호")
        private String address2;

        @Schema(description = "기본으로 지정할지. 켜면 기존 기본은 해제된다", example = "true")
        private Boolean isDefault;
    }

    @Getter
    public static class AddressUpdateRequest {
        private String label;

        @NotBlank
        private String recipientName;

        @NotBlank
        private String recipientPhone;

        @NotBlank
        private String zipCode;

        @NotBlank
        private String address1;

        private String address2;

        private Boolean isDefault;
    }

    @Getter
    @Builder
    @Schema(description = "배송지. 기본 배송지가 목록 맨 앞에 온다")
    public static class AddressResponse {
        private Long id;
        private String label;
        private String recipientName;
        private String recipientPhone;
        private String zipCode;
        private String address1;
        private String address2;

        @Schema(description = "기본 배송지인지. 회원당 하나뿐이다", example = "true")
        private Boolean isDefault;

        public static AddressResponse from(UserAddress address) {
            return AddressResponse.builder()
                    .id(address.getId())
                    .label(address.getLabel())
                    .recipientName(address.getRecipientName())
                    .recipientPhone(address.getRecipientPhone())
                    .zipCode(address.getZipCode())
                    .address1(address.getAddress1())
                    .address2(address.getAddress2())
                    .isDefault(address.getIsDefault())
                    .build();
        }
    }

    public record PushTokenRequest(
            @NotBlank String token
    ) {}
}
