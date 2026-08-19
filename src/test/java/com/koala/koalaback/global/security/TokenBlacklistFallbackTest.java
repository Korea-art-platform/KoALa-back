package com.koala.koalaback.global.security;

import com.koala.koalaback.infra.slack.ServerErrorAlerter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@DisplayName("토큰 블랙리스트 — Redis 가 죽었을 때")
class TokenBlacklistFallbackTest {
    private StringRedisTemplate redisTemplate;
    private ServerErrorAlerter alerter;
    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        alerter = mock(ServerErrorAlerter.class);
        JwtProvider jwtProvider = mock(JwtProvider.class);
        given(jwtProvider.getRemainingExpiryMs(anyString())).willReturn(600_000L);

        service = new TokenBlacklistService(redisTemplate, jwtProvider, alerter);
    }

    @Test
    @DisplayName("조회에 실패하면 통과시킨다 — 막으면 로그인 사용자 전원이 서비스를 못 쓴다")
    void readFailureLetsTheRequestThrough() {
        given(redisTemplate.hasKey(anyString()))
                .willThrow(new RedisConnectionFailureException("connection refused"));

        assertThat(service.isBlacklisted("any-token")).isFalse();
    }

    @Test
    @DisplayName("통과시키되 사람을 부른다 — 조용히 넘어가면 방어가 꺼진 줄 모른다")
    void readFailureRaisesAnAlert() {
        given(redisTemplate.hasKey(anyString()))
                .willThrow(new RedisConnectionFailureException("connection refused"));

        service.isBlacklisted("any-token");

        verify(alerter).report(any(Throwable.class), eq("AUTH"), eq("token-blacklist/read"));
    }

    @Test
    @DisplayName("정상일 때는 그대로 막는다")
    void blacklistedTokenIsStillRejected() {
        given(redisTemplate.hasKey(anyString())).willReturn(true);

        assertThat(service.isBlacklisted("logged-out-token")).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 토큰은 통과한다")
    void unknownTokenPasses() {
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        assertThat(service.isBlacklisted("fresh-token")).isFalse();
    }

    @Test
    @DisplayName("저장에 실패해도 로그아웃은 끝난다 — 리프레시 토큰은 DB 에서 이미 지워졌다")
    void writeFailureDoesNotBreakLogout() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(ops);
        willThrow(new RedisConnectionFailureException("connection refused"))
                .given(ops).set(anyString(), anyString(), any());

        service.blacklist("token");

        verify(alerter).report(any(Throwable.class), eq("LOGOUT"), eq("token-blacklist/write"));
    }
}
