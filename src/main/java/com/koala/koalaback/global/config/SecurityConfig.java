package com.koala.koalaback.global.config;

import com.koala.koalaback.global.security.AdminIpAllowlistFilter;
import com.koala.koalaback.global.security.JwtFilter;
import com.koala.koalaback.global.security.JwtProvider;
import com.koala.koalaback.global.security.RateLimitFilter;
import com.koala.koalaback.global.security.TokenBlacklistService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.koala.koalaback.global.security.oauth2.CustomOAuth2UserService;
import com.koala.koalaback.global.security.oauth2.OAuth2FailureHandler;
import com.koala.koalaback.global.security.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimitFilter rateLimitFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final Environment environment;

    @Value("${admin.allowed-ips:}")
    private String adminAllowedIps;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── 보안 헤더 ──────────────────────────────────────────
                .headers(headers -> headers
                        // HSTS: HTTPS 강제 (1년, 서브도메인 포함)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )
                        // CSP: XSS 방어
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' https://*.tosspayments.com https://*.toss.im; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +
                                        // Sentry 에러 전송 + Toss 결제 API 허용
                                        "connect-src 'self' https://*.sentry.io https://*.ingest.sentry.io " +
                                                "https://*.tosspayments.com https://*.toss.im; " +
                                        // Toss 결제 위젯은 iframe 사용
                                        "frame-src https://*.tosspayments.com https://*.toss.im; " +
                                        "frame-ancestors 'none'; " +
                                        "upgrade-insecure-requests"
                                )
                        )
                        // Clickjacking 방어
                        .frameOptions(frame -> frame.deny())
                        // MIME 스니핑 방지
                        .contentTypeOptions(contentType -> {})
                )

                .authorizeHttpRequests(auth -> {
                        auth.requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset/send",
                                "/api/v1/auth/password-reset/verify",
                                "/api/v1/auth/password-reset/reset"
                        ).permitAll();
                        auth.requestMatchers(HttpMethod.GET, "/api/v1/artists/*/following").authenticated();
                        auth.requestMatchers(HttpMethod.GET,
                                "/api/v1/artists/**",
                                "/api/v1/skus/**",
                                "/api/v1/banners/**",
                                "/api/v1/categories",
                                "/api/v1/notices/**",
                                "/api/v1/app/version").permitAll();
                        // 팔로우/언팔로우는 로그인 필수
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/artists/*/follow").authenticated();
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/artists/*/follow").authenticated();
                        auth.requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**").permitAll();
                        auth.requestMatchers("/webhook/**").permitAll();

                        // Admin 로그인은 인증 없이 허용 (나머지 /admin/api/**는 ADMIN 전용)
                        auth.requestMatchers(HttpMethod.POST, "/admin/api/v1/auth/login").permitAll();

                        // Swagger: 프로덕션에서는 ADMIN 전용, 개발환경에서는 공개
                        if (isProd) {
                            auth.requestMatchers("/swagger-ui/**", "/api-docs/**").hasRole("ADMIN");
                        } else {
                            auth.requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll();
                        }

                        auth.requestMatchers("/actuator/health").permitAll();
                        auth.requestMatchers("/error").permitAll();
                        auth.requestMatchers("/uploads/**").permitAll(); // 로컬 개발용 정적 파일
                        auth.requestMatchers("/admin/api/**").hasRole("ADMIN");
                        auth.anyRequest().authenticated();
                })
                // ── 인증 실패 처리 ────────────────────────────────────────
                // oauth2Login의 기본 동작은 미인증 요청을 OAuth2 로그인 페이지로 302 리다이렉트.
                // API 요청(/api/**)은 리다이렉트 대신 401 JSON을 반환해야 함.
                // 그렇지 않으면 Axios가 Kakao HTML 페이지를 응답으로 받고
                // res.data.data 가 undefined 가 되어 프론트 에러 발생.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}}"
                            );
                        })
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .baseUri("/oauth2/authorization"))
                        .redirectionEndpoint(redir -> redir
                                .baseUri("/login/oauth2/code/*"))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .addFilterBefore(new JwtFilter(jwtProvider, tokenBlacklistService),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter,
                        JwtFilter.class)
                .addFilterBefore(new AdminIpAllowlistFilter(adminAllowedIps),
                        RateLimitFilter.class)
                .build();
    }

    /**
     * RateLimitFilter가 @Component이므로 Spring Boot가 Servlet 필터로 자동 등록함.
     * Security 필터 체인에만 등록하고 Servlet 레벨 이중 실행을 방지.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        CorsConfiguration config = new CorsConfiguration();

        if (isProd) {
            // 프로덕션: 실제 도메인만 허용 (localhost 제외)
            config.setAllowedOriginPatterns(List.of(
                    "https://koala-art.co.kr",
                    "https://www.koala-art.co.kr",
                    "capacitor://localhost",        // Capacitor 모바일 앱 (네이티브 WebView)
                    "https://*.toss.im",            // 앱인토스 미니앱 WebView
                    "https://*.apps-in-toss.toss.im" // 앱인토스 CDN
            ));
        } else {
            // 로컬/개발: localhost 개발 서버 허용
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:[*]",   // 로컬 모든 포트 허용 (5173, 5174 등)
                    "capacitor://localhost",
                    "http://localhost"
            ));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Cache-Control"
        ));
        // HttpOnly 쿠키는 JS에서 직접 접근 불가 — Set-Cookie 노출 불필요
        // config.setExposedHeaders(List.of("Set-Cookie"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}