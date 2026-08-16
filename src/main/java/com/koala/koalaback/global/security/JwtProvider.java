package com.koala.koalaback.global.security;

import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public String createAccessToken(Long userId, String role) {
        return buildToken(String.valueOf(userId), role, TYPE_ACCESS, accessTokenExpiryMs);
    }

    public String createRefreshToken(Long userId) {
        return buildToken(String.valueOf(userId), null, TYPE_REFRESH, refreshTokenExpiryMs);
    }

    private String buildToken(String subject, String role, String type, long expiryMs) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .claim("typ", type)
                .signWith(key);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.compact();
    }

    /** 기존 발급분에는 typ 이 없다 — 없으면 액세스로 본다 */
    public String getTokenType(String token) {
        String type = getClaims(token).get("typ", String.class);
        return type != null ? type : TYPE_ACCESS;
    }

    public Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    public long getRemainingExpiryMs(String token) {
        try {
            long expiry = getClaims(token).getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(0, expiry);
        } catch (BusinessException e) {
            return 0;
        }
    }
}
