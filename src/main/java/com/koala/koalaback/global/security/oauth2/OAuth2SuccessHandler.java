package com.koala.koalaback.global.security.oauth2;

import com.koala.koalaback.domain.user.entity.RefreshToken;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.RefreshTokenRepository;
import com.koala.koalaback.global.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.access-token-expiry-ms:1800000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    @Value("${app.secure-cookies:false}")
    private boolean secureCookies;

    @Value("${oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        String accessToken  = jwtProvider.createAccessToken(user.getId(), "USER");
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        long expirySeconds = refreshTokenExpiryMs / 1000;
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .userId(String.valueOf(user.getId()))
                        .refreshToken(refreshToken)
                        .expiry(expirySeconds)
                        .build()
        );

        String sameSite = secureCookies ? "None" : "Lax";
        response.addHeader("Set-Cookie",
                ResponseCookie.from("accessToken", accessToken)
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/")
                        .maxAge(accessTokenExpiryMs / 1000)
                        .sameSite(sameSite)
                        .build().toString());
        response.addHeader("Set-Cookie",
                ResponseCookie.from("refreshToken", refreshToken)
                        .httpOnly(true)
                        .secure(secureCookies)
                        .path("/api/v1/auth")
                        .maxAge(expirySeconds)
                        .sameSite(sameSite)
                        .build().toString());

        log.info("OAuth2 login success: userId={}", user.getId());
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
