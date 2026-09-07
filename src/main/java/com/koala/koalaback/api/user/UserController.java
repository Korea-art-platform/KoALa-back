package com.koala.koalaback.api.user;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "사용자 · 인증", description = "가입·로그인·토큰, 내 정보, 배송지")
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.access-token-expiry-ms:1800000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Value("${app.secure-cookies:false}")
    private boolean secureCookies;

    @Operation(summary = "회원가입", description = """
            가입과 동시에 이 계정의 장바구니를 만든다. 처음 담을 때 만들면 그 순간
            실패할 자리가 하나 생긴다.

            가입 전에 같은 이메일로 비회원 주문을 했다면 그 주문을 이 계정에 붙인다.
            이메일은 가입 과정에서 확인된 값이라 그 사람 것이 맞다. 이미 주인이 있는
            주문은 붙지 않는다. 이 작업은 가입 트랜잭션이 커밋된 뒤에 일어난다 —
            주문 쪽에서 실패해도 가입은 남는다.

            토큰은 응답 본문과 함께 httpOnly 쿠키로도 내려간다.
            """)
    @PostMapping("/api/v1/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto.TokenResponse> signup(
            @Valid @RequestBody UserDto.SignupRequest req,
            HttpServletResponse response) {
        UserDto.TokenResponse tokens = userService.signup(req);
        setTokenCookies(response, tokens);
        return ApiResponse.ok(tokens);
    }

    @Operation(summary = "로그인", description = """
            이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 오류(INVALID_CREDENTIALS)로
            돌려준다. 다르게 답하면 이메일만 넣어 보는 것으로 가입 여부를 알아낼 수 있다.

            탈퇴·정지·휴면 계정은 각각 다른 오류로 구분한다. 이건 본인이 왜 못 들어오는지
            알아야 하는 정보다.

            토큰은 httpOnly 쿠키로 내려간다. 접근 토큰은 전 경로, 갱신 토큰은
            /api/v1/auth 아래에서만 실린다 — 갱신 토큰이 필요 없는 요청에 딸려 가지
            않게 한다.
            """)
    @PostMapping("/api/v1/auth/login")
    public ApiResponse<UserDto.TokenResponse> login(
            @Valid @RequestBody UserDto.LoginRequest req,
            HttpServletResponse response) {
        UserDto.TokenResponse tokens = userService.login(req);
        setTokenCookies(response, tokens);
        return ApiResponse.ok(tokens);
    }

    @Operation(summary = "토큰 갱신", description = """
            쿠키의 갱신 토큰으로 새 토큰을 받는다. 본문으로 받지 않는다.

            서명 검증만으로 통과시키지 않고, 저장된 토큰과 글자까지 같은지 대조한다.
            로그아웃하면 저장된 쪽이 지워지므로, 서명이 아직 유효한 토큰을 들고 있어도
            다시 들어올 수 없다.
            """)
    @PostMapping("/api/v1/auth/refresh")
    public ApiResponse<Void> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        UserDto.TokenResponse tokens = userService.refresh(refreshToken);
        setTokenCookies(response, tokens);
        return ApiResponse.ok();
    }

    @Operation(summary = "로그아웃", description = """
            저장된 갱신 토큰을 지우고, 아직 만료되지 않은 접근 토큰을 블랙리스트에 올린
            뒤 쿠키를 비운다.

            JWT 는 발급하고 나면 서버가 회수할 수 없다. 블랙리스트가 없으면 로그아웃해도
            남은 유효기간 동안 그 토큰이 계속 통한다.
            """)
    @PostMapping("/api/v1/auth/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal Long userId,
            @CookieValue(name = "accessToken", required = false) String accessToken,
            HttpServletResponse response) {
        userService.logout(userId);
        if (accessToken != null) {
            tokenBlacklistService.blacklist(accessToken);
        }
        clearTokenCookies(response);
        return ApiResponse.ok();
    }

    @Operation(summary = "내 정보")
    @GetMapping("/api/v1/users/me")
    public ApiResponse<UserDto.ProfileResponse> getProfile(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getProfile(userId));
    }

    @Operation(summary = "내 정보 수정", description = """
            보낸 항목만 바꾼다. 비운 항목은 기존 값을 그대로 둔다. 휴대폰번호는 E.164
            형식으로 맞춰 저장한다 — 사람마다 다르게 적은 번호가 같은 값으로 모인다.
            """)
    @PatchMapping("/api/v1/users/me")
    public ApiResponse<UserDto.ProfileResponse> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDto.UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(userId, req));
    }

    @Operation(summary = "비밀번호 변경", description = """
            로그인한 상태여도 현재 비밀번호를 다시 확인한다. 자리를 비운 사이 남이
            브라우저를 만졌을 때 비밀번호까지 바뀌면 계정을 통째로 잃는다.
            """)
    @PatchMapping("/api/v1/users/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDto.ChangePasswordRequest req) {
        userService.changePassword(userId, req);
        return ApiResponse.ok();
    }

    @Operation(summary = "회원 탈퇴", description = """
            소프트 삭제다. 행을 지우지 않고 탈퇴 표시만 남긴다 — 지나간 주문과 결제가
            이 사용자를 가리키고 있어 실제로 지우면 거래 기록이 끊긴다.

            갱신 토큰은 함께 지운다. 남겨 두면 탈퇴한 계정으로 토큰을 갱신할 수 있다.
            """)
    @DeleteMapping("/api/v1/users/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "푸시 토큰 등록", description = "앱 알림에 쓰는 FCM 토큰을 계정에 붙인다.")
    @PostMapping("/api/v1/users/me/push-token")
    public ApiResponse<Void> savePushToken(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserDto.PushTokenRequest req) {
        userService.saveFcmToken(userId, req.token());
        return ApiResponse.ok();
    }

    @Operation(summary = "배송지 목록", description = "기본 배송지가 맨 앞, 그다음은 최근에 만든 순이다.")
    @GetMapping("/api/v1/users/me/addresses")
    public ApiResponse<List<UserDto.AddressResponse>> getAddresses(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getAddresses(userId));
    }

    @Operation(summary = "배송지 등록", description = """
            첫 배송지는 요청과 무관하게 기본이 된다. 기본이 하나도 없으면 주문할 때
            고를 것이 비어 있다.

            기본으로 지정하면 기존 기본은 해제된다 — 기본은 언제나 하나다.
            """)
    @PostMapping("/api/v1/users/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto.AddressResponse> createAddress(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDto.AddressCreateRequest req) {
        return ApiResponse.ok(userService.createAddress(userId, req));
    }

    @Operation(summary = "배송지 수정", description = """
            주소 id 와 userId 를 함께 걸어 찾는다. 남의 주소 id 를 알아도 열리지 않는다.
            """)
    @PutMapping("/api/v1/users/me/addresses/{addressId}")
    public ApiResponse<UserDto.AddressResponse> updateAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId,
            @Valid @RequestBody UserDto.AddressUpdateRequest req) {
        return ApiResponse.ok(userService.updateAddress(userId, addressId, req));
    }

    @Operation(summary = "기본 배송지 지정", description = "기존 기본을 해제하고 이 주소를 기본으로 둔다.")
    @PatchMapping("/api/v1/users/me/addresses/{addressId}/default")
    public ApiResponse<Void> setDefaultAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId) {
        userService.setDefaultAddress(userId, addressId);
        return ApiResponse.ok();
    }

    @Operation(summary = "배송지 삭제")
    @DeleteMapping("/api/v1/users/me/addresses/{addressId}")
    public ApiResponse<Void> deleteAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId) {
        userService.deleteAddress(userId, addressId);
        return ApiResponse.ok();
    }

    private void setTokenCookies(HttpServletResponse response, UserDto.TokenResponse tokens) {
        String sameSite = secureCookies ? "None" : "Lax";
        response.addHeader("Set-Cookie",
                ResponseCookie.from("accessToken", tokens.getAccessToken())
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/")
                        .maxAge(accessTokenExpiryMs / 1000)
                        .sameSite(sameSite)
                        .build().toString());
        response.addHeader("Set-Cookie",
                ResponseCookie.from("refreshToken", tokens.getRefreshToken())
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/api/v1/auth")
                        .maxAge(refreshTokenExpiryMs / 1000)
                        .sameSite(sameSite)
                        .build().toString());
    }

    private void clearTokenCookies(HttpServletResponse response) {
        String sameSite = secureCookies ? "None" : "Lax";
        response.addHeader("Set-Cookie",
                ResponseCookie.from("accessToken", "")
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/")
                        .maxAge(0)
                        .sameSite(sameSite)
                        .build().toString());
        response.addHeader("Set-Cookie",
                ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/api/v1/auth")
                        .maxAge(0)
                        .sameSite(sameSite)
                        .build().toString());
    }
}
