package com.koala.koalaback.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PII 컬럼 단위 AES-256-GCM 암호화 컨버터.
 *
 * 저장 형식: "enc:" + Base64( IV(12B) + cipherText + GCMTag(16B) )
 * - "enc:" 접두어가 없는 값(기존 평문)은 읽을 때 그대로 반환 → 점진적 마이그레이션 가능.
 * - 키가 미설정이면 평문 그대로 저장(서비스 다운 방지). 운영에선 반드시 키 설정.
 *
 * 키는 {@link CryptoKeyInitializer}가 앱 기동 시 {@link #initKey(String)}로 주입한다.
 * (JPA 컨버터는 Spring 빈이 아니라 @Value 직접 주입이 불가하므로 정적 키 홀더 방식 사용)
 */
@Converter
public class AesGcmCryptoConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec keySpec;

    /** 앱 기동 시 CryptoKeyInitializer가 호출 */
    static void initKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            keySpec = null;
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        if (keySpec == null) return attribute;          // 키 없으면 평문 저장
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv).put(cipherText).array();
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("PII 암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (!dbData.startsWith(PREFIX)) return dbData;  // 기존 평문 데이터 호환
        if (keySpec == null) throw new IllegalStateException("PII 암호화 키 미설정인데 암호문 발견");
        try {
            byte[] combined = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            ByteBuffer bb = ByteBuffer.wrap(combined);

            byte[] iv = new byte[IV_LENGTH];
            bb.get(iv);
            byte[] cipherText = new byte[bb.remaining()];
            bb.get(cipherText);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII 복호화 실패", e);
        }
    }
}
