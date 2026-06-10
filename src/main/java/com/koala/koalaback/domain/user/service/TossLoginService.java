package com.koala.koalaback.domain.user.service;

import com.koala.koalaback.domain.user.dto.UserDto;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.security.JwtProvider;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * 앱인토스 토스 로그인 서비스
 *
 * 플로우:
 * 1. 프론트 → appLogin() → authorizationCode 획득
 * 2. 프론트 → 백엔드 POST /api/v1/auth/toss/login { authorizationCode }
 * 3. 백엔드 → 토스 서버에 토큰 교환 요청 (mTLS)
 * 4. 백엔드 → 사용자 정보 조회 (AES-256-GCM 복호화)
 * 5. 백엔드 → 기존 유저 연동 or 신규 생성 → JWT 발급
 *
 * TODO: 앱인토스 콘솔에서 발급받은 mTLS 인증서 설정 필요
 * TODO: AES-256-GCM 복호화 키 (이메일로 수령 후 환경변수 설정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TossLoginService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final CodeGenerator codeGenerator;
    private final UserService userService;

    // 토스 mTLS RestTemplate 주입 (TossMtlsConfig의 @Bean("tossRestTemplate"))
    private final RestTemplate tossRestTemplate;

    // TODO: 앱인토스 콘솔 → 내 앱 → 설정 → API 키에서 확인
    @Value("${apps-in-toss.base-url:https://pay-apps-in-toss-api.toss.im}")
    private String tossApiBaseUrl;

    // TODO: 앱인토스 콘솔에서 발급받은 mTLS 인증서 경로
    @Value("${apps-in-toss.cert-path:}")
    private String certPath;

    // TODO: 앱인토스에서 이메일로 전달하는 AES 복호화 키 (Base64)
    @Value("${apps-in-toss.aes-key:}")
    private String aesKeyBase64;

    // TODO: 앱인토스에서 이메일로 전달하는 AAD 값
    @Value("${apps-in-toss.aes-aad:}")
    private String aesAad;

    private static final String OAUTH_PROVIDER_TOSS = "TOSS";

    /**
     * 토스 로그인 처리
     *
     * @param authorizationCode 앱인토스 SDK appLogin()에서 반환된 인가 코드
     * @return JWT 토큰 (accessToken + refreshToken)
     */
    @Transactional
    public UserDto.TokenResponse login(String authorizationCode) {
        // 1. 인가 코드 → 액세스 토큰 교환
        String tossAccessToken = exchangeToken(authorizationCode);

        // 2. 사용자 정보 조회
        TossUserInfo userInfo = fetchUserInfo(tossAccessToken);

        // 3. 기존 유저 조회 (TOSS oauthId로 먼저 검색)
        User user = userRepository.findByOauthProviderAndOauthId(OAUTH_PROVIDER_TOSS, userInfo.userKey())
                .orElseGet(() -> {
                    // 이메일이 있으면 기존 계정 연동
                    if (userInfo.email() != null) {
                        return userRepository.findByEmail(userInfo.email())
                                .map(existing -> {
                                    existing.linkOAuth(OAUTH_PROVIDER_TOSS, userInfo.userKey());
                                    return existing;
                                })
                                .orElseGet(() -> createTossUser(userInfo));
                    }
                    return createTossUser(userInfo);
                });

        user.updateLastLoginAt();
        return userService.issueTokens(user);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * 인가 코드 → 토스 액세스 토큰 교환
     *
     * TODO: mTLS RestTemplate 교체 필요
     * 현재는 일반 RestTemplate 사용 (실제 환경에서는 mTLS 인증서 적용 필수)
     */
    @SuppressWarnings("unchecked")
    private String exchangeToken(String authorizationCode) {
        RestTemplate restTemplate = tossRestTemplate;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("authorizationCode", authorizationCode);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tossApiBaseUrl + "/api-partner/v1/apps-in-toss/user/oauth2/generate-token",
                    request,
                    Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(ErrorCode.TOSS_TOKEN_EXCHANGE_FAILED);
            }

            Map<String, Object> responseBody = response.getBody();
            return (String) responseBody.get("accessToken");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossLogin] 토큰 교환 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOSS_TOKEN_EXCHANGE_FAILED);
        }
    }

    /**
     * 토스 사용자 정보 조회 + AES-256-GCM 복호화
     *
     * TODO: mTLS RestTemplate 교체 필요
     */
    @SuppressWarnings("unchecked")
    private TossUserInfo fetchUserInfo(String tossAccessToken) {
        RestTemplate restTemplate = tossRestTemplate;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tossAccessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    tossApiBaseUrl + "/api-partner/v1/apps-in-toss/user/oauth2/login-me",
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new BusinessException(ErrorCode.TOSS_USER_INFO_FAILED);
            }

            Map<String, Object> body = response.getBody();
            String userKey = (String) body.get("userKey");

            // 개인정보는 AES-256-GCM 암호화 상태로 전달됨
            String encryptedName = (String) body.get("name");
            String encryptedEmail = (String) body.get("email");
            String encryptedPhone = (String) body.get("phoneNumber");

            String name = decryptIfPresent(encryptedName);
            String email = decryptIfPresent(encryptedEmail);
            String phone = decryptIfPresent(encryptedPhone);

            return new TossUserInfo(userKey, name, email, phone);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossLogin] 사용자 정보 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOSS_USER_INFO_FAILED);
        }
    }

    /**
     * AES-256-GCM 복호화
     * IV(12바이트) + 암호문 구조로 Base64 인코딩된 값을 복호화
     *
     * TODO: aesKeyBase64, aesAad 환경변수 설정 필요 (앱인토스에서 이메일로 전달)
     */
    private String decryptIfPresent(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isBlank()) return null;
        if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
            log.warn("[TossLogin] AES 복호화 키가 설정되지 않았습니다. 암호화된 원문을 반환합니다.");
            return encryptedBase64; // TODO: 키 설정 전 임시 처리
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);

            // IV: 앞 12바이트
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[encryptedBytes.length - 12];
            System.arraycopy(encryptedBytes, 0, iv, 0, 12);
            System.arraycopy(encryptedBytes, 12, cipherText, 0, cipherText.length);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec paramSpec = new GCMParameterSpec(128, iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, paramSpec);

            if (aesAad != null && !aesAad.isBlank()) {
                cipher.updateAAD(aesAad.getBytes());
            }

            return new String(cipher.doFinal(cipherText));
        } catch (Exception e) {
            log.error("[TossLogin] AES 복호화 실패: {}", e.getMessage());
            return null;
        }
    }

    private User createTossUser(TossUserInfo info) {
        String name = info.name() != null ? info.name() : "토스 사용자";
        String email = info.email() != null
                ? info.email()
                : "toss_" + info.userKey() + "@apps-in-toss.noemail";

        return userRepository.save(
                User.createOAuthUser(
                        codeGenerator.generateCode(),
                        email,
                        name,
                        OAUTH_PROVIDER_TOSS,
                        info.userKey()
                )
        );
    }

    /** 앱인토스 사용자 정보 DTO */
    public record TossUserInfo(
            String userKey,
            String name,
            String email,
            String phone
    ) {}
}
