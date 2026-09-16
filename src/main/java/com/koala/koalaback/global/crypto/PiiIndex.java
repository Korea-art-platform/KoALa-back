package com.koala.koalaback.global.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class PiiIndex {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String DERIVE_LABEL = "pii-index-v1";
    private static final String EMAIL_LABEL = "orderer-email";
    private static final String PHONE_LABEL = "orderer-phone";

    private final byte[] indexKey;

    public PiiIndex(@Value("${pii.encryption.key:}") String base64Key) {
        this.indexKey = deriveKey(base64Key);
        if (this.indexKey == null) {
            log.warn("[PII] 암호화 키 미설정 — 주문자 찾기용 해시를 만들지 않습니다 (로컬/테스트 전용)");
        }
    }

    public boolean isEnabled() {
        return indexKey != null;
    }

    public String ofEmail(String email) {
        if (email == null) return null;
        return hash(EMAIL_LABEL, email.trim().toLowerCase());
    }

    public String ofPhone(String phone) {
        return hash(PHONE_LABEL, digitsOf(phone));
    }

    public String last4Of(String phone) {
        String digits = digitsOf(phone);
        if (digits == null || digits.length() < 4) return null;
        return digits.substring(digits.length() - 4);
    }

    private String digitsOf(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private String hash(String label, String value) {
        if (indexKey == null || value == null || value.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(indexKey, ALGORITHM));
            mac.update(label.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("PII 찾기용 해시 계산 실패", e);
        }
    }

    private static byte[] deriveKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) return null;
        try {
            byte[] master = Base64.getDecoder().decode(base64Key);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(master, ALGORITHM));
            return mac.doFinal(DERIVE_LABEL.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("PII 찾기용 키 유도 실패", e);
        }
    }
}
