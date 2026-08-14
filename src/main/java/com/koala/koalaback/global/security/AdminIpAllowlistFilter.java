package com.koala.koalaback.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class AdminIpAllowlistFilter extends OncePerRequestFilter {
    private static final String ADMIN_PREFIX = "/admin/api/";

    private static final String ADMIN_LOGIN_PATH = "/admin/api/v1/auth/login";

    private static final Set<String> TRUSTED_PROXY_PREFIXES = Set.of(
            "127.", "10.", "172.16.", "172.17.", "172.18.", "172.19.", "172.20.",
            "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.", "192.168.", "0:0:0:0:0:0:0:1", "::1"
    );

    private final Set<String> allowedIps;

    public AdminIpAllowlistFilter(String allowedIpsConfig) {
        if (allowedIpsConfig == null || allowedIpsConfig.isBlank()) {
            this.allowedIps = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
        } else {
            this.allowedIps = Arrays.stream(allowedIpsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (!uri.startsWith(ADMIN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (uri.equals(ADMIN_LOGIN_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (allowedIps.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);

        if (!allowedIps.contains(clientIp)) {
            log.warn("Admin 접근 거부 — IP: {}, URI: {}", clientIp, uri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"허용되지 않은 IP 주소입니다.\"}}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        boolean fromTrustedProxy = TRUSTED_PROXY_PREFIXES.stream()
                .anyMatch(remoteAddr::startsWith)
                || "::1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr);

        if (fromTrustedProxy) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        return remoteAddr;
    }
}
