package com.koala.koalaback.domain.user.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@Getter
@Builder
@RedisHash("refresh_token")
public class RefreshToken {
    @Id
    private String userId;

    private String refreshToken;

    @TimeToLive
    private Long expiry;
}
