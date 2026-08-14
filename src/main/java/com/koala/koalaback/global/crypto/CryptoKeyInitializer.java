package com.koala.koalaback.global.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CryptoKeyInitializer {
    @Value("${pii.encryption.key:}")
    private String piiKey;

    @PostConstruct
    public void init() {
        AesGcmCryptoConverter.initKey(piiKey);
        if (piiKey == null || piiKey.isBlank()) {
            log.warn("[PII] 암호화 키 미설정 — PII가 평문으로 저장됩니다. 운영에선 PII_ENCRYPTION_KEY 설정 필요");
        } else {
            log.info("[PII] 암호화 키 로드 완료");
        }
    }
}
