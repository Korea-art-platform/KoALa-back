package com.koala.koalaback.global.security;

import com.koala.koalaback.global.util.IpResolverUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redisTemplate;

    private static final int    SENSITIVE_LIMIT      = 5;
    private static final int    SENSITIVE_WINDOW_SEC = 60;

    private static final int    GLOBAL_LIMIT         = 200;
    private static final int    GLOBAL_WINDOW_SEC    = 60;

    private static final Set<String> SENSITIVE_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/password-reset/send",
            "/admin/api/v1/auth/login"
    );

    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1])\n" +
            "if tonumber(c) == 1 then\n" +
            "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))\n" +
            "end\n" +
            "return c",
            Long.class
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip   = resolveClientIp(request);

        if (SENSITIVE_PATHS.contains(path)) {
            if (!isAllowed(ip, "sensitive:" + path, SENSITIVE_LIMIT, SENSITIVE_WINDOW_SEC)) {
                log.warn("[RateLimit] 민감 경로 초과 — ip={}, path={}", ip, path);
                writeRateLimitResponse(response);
                return;
            }
        }

        if (path.startsWith("/api/") || path.startsWith("/admin/api/")) {
            if (!isAllowed(ip, "global", GLOBAL_LIMIT, GLOBAL_WINDOW_SEC)) {
                log.warn("[RateLimit] 전체 API 초과 — ip={}", ip);
                writeRateLimitResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String ip, String scope, int limit, int windowSec) {
        String key = "rl:" + scope + ":" + ip;
        try {
            Long count = redisTemplate.execute(
                    INCR_SCRIPT,
                    List.of(key),
                    String.valueOf(windowSec)
            );
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("[RateLimit] Redis 오류, 요청 허용 처리: {}", e.getMessage());
            return true;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        return IpResolverUtil.resolve(request);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"C008\"," +
                "\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}"
        );
    }
}
