package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.admin.dto.AdminDto;
import com.koala.koalaback.domain.admin.entity.AdminAuditLog;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.security.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/admin/api/v1")
@RequiredArgsConstructor
public class AdminController {
    private static final String ADMIN_COOKIE = "admin_token";

    private final AdminService adminService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.secure-cookies:false}")
    private boolean secureCookies;

    @PostMapping("/auth/login")
    public ApiResponse<AdminDto.TokenResponse> login(
            @Valid @RequestBody AdminDto.LoginRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        AdminDto.TokenResponse tokenRes = adminService.login(req, httpReq);

        httpResp.addHeader("Set-Cookie",
                ResponseCookie.from(ADMIN_COOKIE, tokenRes.getAccessToken())
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/admin/api/")
                        .maxAge(60 * 60 * 8)
                        .sameSite("Strict")
                        .build().toString());

        return ApiResponse.ok(tokenRes);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest httpReq,
            HttpServletResponse httpResp) {
        Cookie cookie = WebUtils.getCookie(httpReq, ADMIN_COOKIE);
        if (cookie != null) {
            tokenBlacklistService.blacklist(cookie.getValue());
        }

        String authHeader = httpReq.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklistService.blacklist(authHeader.substring(7).trim());
        }

        httpResp.addHeader("Set-Cookie",
                ResponseCookie.from(ADMIN_COOKIE, "")
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/admin/api/")
                        .maxAge(0)
                        .sameSite("Strict")
                        .build().toString());

        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/me")
    public ApiResponse<AdminDto.AdminResponse> getMyInfo(
            @AuthenticationPrincipal Long adminId) {
        return ApiResponse.ok(adminService.getMyInfo(adminId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/skus/stock-adjust")
    public ApiResponse<Void> adjustStock(
            @AuthenticationPrincipal Long adminId,
            @Valid @RequestBody AdminDto.StockAdjustRequest req,
            HttpServletRequest httpReq) {
        adminService.adjustStock(adminId, req, httpReq);
        return ApiResponse.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLog>> getAuditLogs(
            @AuthenticationPrincipal Long adminId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(adminService.getAuditLogs(adminId, pageable));
    }
}
