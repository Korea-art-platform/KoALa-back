package com.koala.koalaback.global.crypto;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CryptoKeyInitializer {

    // aes 키 길이 (바이트)
    private static final Set<Integer> VALID_KEY_LENGTHS = Set.of(16, 24, 32);

    private final Environment environment;

    @Value("${pii.encryption.key:}")
    private String piiKey;

    @PostConstruct
    public void init() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (piiKey == null || piiKey.isBlank()) {
            // 키가 없으면 이름·전화·주소가 평문으로 쌓인다. 그리고 나중에 키를 넣어도
            // 그 사이 저장된 행은 평문 그대로 남는다 — 조용히 진행되면 안 된다
            if (isProd) {
                throw new IllegalStateException(
                        "[PII] 운영 환경에 암호화 키가 없습니다. PII_ENCRYPTION_KEY 를 설정한 뒤 기동하세요.");
            }
            log.warn("[PII] 암호화 키 미설정 — PII가 평문으로 저장됩니다 (로컬/테스트 전용)");
            AesGcmCryptoConverter.initKey(null);
            return;
        }

        validateKeyLength();
        AesGcmCryptoConverter.initKey(piiKey);
        log.info("[PII] 암호화 키 로드 완료");
    }

    // 길이가 틀리면 기동은 되고 저장할 때마다 터진다 — 여기서 먼저 걸러낸다
    private void validateKeyLength() {
        int length;
        try {
            length = Base64.getDecoder().decode(piiKey).length;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("[PII] 암호화 키가 Base64 형식이 아닙니다.", e);
        }
        if (!VALID_KEY_LENGTHS.contains(length)) {
            throw new IllegalStateException(
                    "[PII] 암호화 키 길이가 잘못됐습니다: " + length + "바이트 (16/24/32 만 허용)");
        }
    }
}
