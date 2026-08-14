package com.koala.koalaback.global.security.oauth2;

import java.util.Map;

public class KakaoOAuth2UserInfo implements OAuth2UserInfo {
    private final Map<String, Object> attributes;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getOauthId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getEmail() {
        Map<String, Object> kakaoAccount =
                (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount != null) {
            String email = (String) kakaoAccount.get("email");
            if (email != null) return email;
        }
        return "kakao_" + getOauthId() + "@kakao.placeholder";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getName() {
        Map<String, Object> kakaoAccount =
                (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount == null) return "카카오 사용자";
        Map<String, Object> profile =
                (Map<String, Object>) kakaoAccount.get("profile");
        if (profile == null) return "카카오 사용자";
        return (String) profile.get("nickname");
    }

    @Override
    public String getProvider() { return "KAKAO"; }
}
