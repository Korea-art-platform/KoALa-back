package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.entity.OnSitePaymentSetting;
import com.koala.koalaback.domain.order.repository.OnSitePaymentSettingRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnSiteAccessService {
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final String HMAC = "HmacSHA256";

    private final OnSitePaymentSettingRepository settingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String secret;

    public OnSitePaymentDto.StatusResponse status() {
        return OnSitePaymentDto.StatusResponse.builder().enabled(load().isOn()).build();
    }

    public OnSitePaymentDto.SettingResponse getSetting() {
        return OnSitePaymentDto.SettingResponse.from(load());
    }

    @Transactional
    public OnSitePaymentDto.SettingResponse updateSetting(OnSitePaymentDto.SettingUpdateRequest req) {
        OnSitePaymentSetting setting = settingRepository.findById(OnSitePaymentSetting.SINGLETON_ID)
                .orElseGet(() -> settingRepository.save(OnSitePaymentSetting.initial()));

        if (req.getPin() != null && !req.getPin().isBlank()) {
            setting.changePin(passwordEncoder.encode(req.getPin()));
        }
        if (Boolean.TRUE.equals(req.getEnabled())) {
            if (!setting.hasPin()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "PIN 을 먼저 정해 주세요.");
            }
            setting.enable();
        } else if (Boolean.FALSE.equals(req.getEnabled())) {
            setting.disable();
        }

        log.info("On-site payment setting changed: enabled={}, version={}",
                setting.isOn(), setting.getVersion());
        return OnSitePaymentDto.SettingResponse.from(setting);
    }

    public OnSitePaymentDto.SessionResponse openSession(String pin) {
        OnSitePaymentSetting setting = load();
        if (!setting.isOn()) {
            throw new BusinessException(ErrorCode.ONSITE_DISABLED);
        }
        if (pin == null || !passwordEncoder.matches(pin, setting.getPinHash())) {
            log.warn("On-site PIN mismatch");
            throw new BusinessException(ErrorCode.ONSITE_PIN_INVALID);
        }

        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        String payload = setting.getVersion() + "." + expiresAt.getEpochSecond();
        return OnSitePaymentDto.SessionResponse.builder()
                .sessionToken(payload + "." + sign(payload))
                .expiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()))
                .build();
    }

    public void verifySession(String token) {
        OnSitePaymentSetting setting = load();
        if (!setting.isOn()) {
            throw new BusinessException(ErrorCode.ONSITE_DISABLED);
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.ONSITE_SESSION_EXPIRED);
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(ErrorCode.ONSITE_SESSION_EXPIRED);
        }
        String payload = parts[0] + "." + parts[1];
        boolean signed = MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8));

        long version;
        long expiresAt;
        try {
            version = Long.parseLong(parts[0]);
            expiresAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ONSITE_SESSION_EXPIRED);
        }

        if (!signed || version != setting.getVersion() || Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException(ErrorCode.ONSITE_SESSION_EXPIRED);
        }
    }

    private OnSitePaymentSetting load() {
        return settingRepository.findById(OnSitePaymentSetting.SINGLETON_ID)
                .orElseGet(OnSitePaymentSetting::initial);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(("onsite:" + secret).getBytes(StandardCharsets.UTF_8), HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("현장결제 입장권 서명에 실패했습니다.", e);
        }
    }
}
