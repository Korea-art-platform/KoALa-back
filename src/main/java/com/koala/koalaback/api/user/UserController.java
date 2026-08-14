package com.koala.koalaback.api.user;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/api/v1/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto.TokenResponse> signup(
            @Valid @RequestBody UserDto.SignupRequest req,
            HttpServletResponse response) {
        UserDto.TokenResponse tokens = userService.signup(req);
        setTokenCookies(response, tokens);
        return ApiResponse.ok(tokens);
    }

    @PostMapping("/api/v1/auth/login")
    public ApiResponse<UserDto.TokenResponse> login(
            @Valid @RequestBody UserDto.LoginRequest req,
            HttpServletResponse response) {
        UserDto.TokenResponse tokens = userService.login(req);
        setTokenCookies(response, tokens);
        return ApiResponse.ok(tokens);
    }

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

    @GetMapping("/api/v1/users/me")
    public ApiResponse<UserDto.ProfileResponse> getProfile(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getProfile(userId));
    }

    @PatchMapping("/api/v1/users/me")
    public ApiResponse<UserDto.ProfileResponse> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDto.UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(userId, req));
    }

    @PatchMapping("/api/v1/users/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDto.ChangePasswordRequest req) {
        userService.changePassword(userId, req);
        return ApiResponse.ok();
    }

    @DeleteMapping("/api/v1/users/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ApiResponse.ok();
    }

    @PostMapping("/api/v1/users/me/push-token")
    public ApiResponse<Void> savePushToken(
            @AuthenticationPrincipal Long userId,
            @RequestBody UserDto.PushTokenRequest req) {
        userService.saveFcmToken(userId, req.token());
        return ApiResponse.ok();
    }

    @GetMapping("/api/v1/users/me/addresses")
    public ApiResponse<List<UserDto.AddressResponse>> getAddresses(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getAddresses(userId));
    }

    @PostMapping("/api/v1/users/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDto.AddressResponse> createAddress(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDto.AddressCreateRequest req) {
        return ApiResponse.ok(userService.createAddress(userId, req));
    }

    @PutMapping("/api/v1/users/me/addresses/{addressId}")
    public ApiResponse<UserDto.AddressResponse> updateAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId,
            @Valid @RequestBody UserDto.AddressUpdateRequest req) {
        return ApiResponse.ok(userService.updateAddress(userId, addressId, req));
    }

    @PatchMapping("/api/v1/users/me/addresses/{addressId}/default")
    public ApiResponse<Void> setDefaultAddress(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId) {
        userService.setDefaultAddress(userId, addressId);
        return ApiResponse.ok();
    }

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
