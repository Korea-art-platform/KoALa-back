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
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "재고 수동 조정", description = """
            재고를 직접 늘리거나 줄인다. delta 는 증감분이지 최종 수량이 아니다 —
            장부에 한 줄을 더하는 방식이라 지금 수량을 몰라도 "3개 늘림"을 적을 수 있고,
            무엇을 얼마나 바꿨는지가 그대로 남는다.

            조정 후 수량이 0 이하면 품절로, 품절이던 것이 0 을 넘으면 판매중으로 상태가
            함께 바뀐다. 관리자가 상태까지 따로 만질 필요가 없다.

            조정 전후 수량과 메모를 감사 로그(STOCK_ADJUST)에 남긴다. 기록하는 '이후'
            수량은 계산값이 아니라 조정 뒤 다시 읽은 실제 값이다.
            """)
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
