package com.koala.koalaback.global.security;

import com.koala.koalaback.infra.slack.ServerErrorAlerter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    private static final String KEY_PREFIX = "token_blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProvider jwtProvider;
    private final ServerErrorAlerter serverErrorAlerter;

    public void blacklist(String token) {
        long remainingMs = jwtProvider.getRemainingExpiryMs(token);
        if (remainingMs <= 0) return;
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + token,
                    "1",
                    Duration.ofMillis(remainingMs)
            );
        } catch (Exception e) {
            log.warn("[TokenBlacklist] Redis 저장 실패 — 로그아웃은 계속 진행됨: {}", e.getMessage());
            serverErrorAlerter.report(e, "LOGOUT", "token-blacklist/write");
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
        } catch (Exception e) {
            log.error("★[TokenBlacklist] Redis 조회 실패 — 블랙리스트 미적용 상태로 통과시킨다: {}",
                    e.getMessage());
            serverErrorAlerter.report(e, "AUTH", "token-blacklist/read");
            return false;
        }
    }
}
