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

                .headers(headers -> headers

                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)
                        )

                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' https://*.tosspayments.com https://*.toss.im https://pay.nicepay.co.kr " +
                                                "https://cpay.payple.kr https://democpay.payple.kr; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data: https:; " +
                                        "font-src 'self' data:; " +

                                        "connect-src 'self' https://*.sentry.io https://*.ingest.sentry.io " +
                                                "https://*.tosspayments.com https://*.toss.im " +
                                                "https://*.nicepay.co.kr https://*.payple.kr; " +

                                        "frame-src https://*.tosspayments.com https://*.toss.im " +
                                                "https://*.nicepay.co.kr https://*.payple.kr; " +
                                        "frame-ancestors 'none'; " +
                                        "upgrade-insecure-requests"
                                )
                        )

                        .frameOptions(frame -> frame.deny())

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

                        auth.requestMatchers(HttpMethod.POST, "/api/v1/artists/*/follow").authenticated();
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/artists/*/follow").authenticated();
                        auth.requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**").permitAll();
                        auth.requestMatchers("/webhook/**").permitAll();
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/payments/nice/return").permitAll();
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/payments/payple/return").permitAll();

                        auth.requestMatchers(HttpMethod.POST, "/admin/api/v1/auth/login").permitAll();

                        if (isProd) {
                            auth.requestMatchers("/swagger-ui/**", "/api-docs/**").hasRole("ADMIN");
                        } else {
                            auth.requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll();
                        }

                        auth.requestMatchers("/actuator/health").permitAll();
                        auth.requestMatchers("/error").permitAll();
                        auth.requestMatchers("/uploads/**").permitAll();
                        auth.requestMatchers("/admin/api/**").hasRole("ADMIN");
                        auth.anyRequest().authenticated();
                })

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
            config.setAllowedOriginPatterns(List.of(
                    "https://koala-art.co.kr",
                    "https://www.koala-art.co.kr",

                    "capacitor://localhost"
            ));
        } else {
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:[*]",
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

        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
