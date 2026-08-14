package com.koala.koalaback.global.security.oauth2;

import java.util.Map;

public class OAuth2UserInfoFactory {
    public static OAuth2UserInfo getOAuth2UserInfo(String provider,
                                                   Map<String, Object> attributes) {
        return switch (provider.toUpperCase()) {
            case "KAKAO" -> new KakaoOAuth2UserInfo(attributes);
            case "NAVER" -> new NaverOAuth2UserInfo(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 소셜 로그인: " + provider);
        };
    }
}
