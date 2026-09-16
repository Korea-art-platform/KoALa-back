package com.koala.koalaback.global.security;

import com.koala.koalaback.global.util.ClientIpResolver;
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

    private final Set<String> allowedIps;
    private final ClientIpResolver clientIpResolver;

    public AdminIpAllowlistFilter(String allowedIpsConfig, ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
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
        return clientIpResolver.resolve(request);
    }
}
