package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("현장결제 페이지 입장")
class OnSiteAccessServiceTest extends IntegrationTestSupport {
    private static final String PIN = "482915";

    @Autowired private OnSiteAccessService onSiteAccessService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void reset() {
        jdbcTemplate.update("UPDATE onsite_payment_settings SET enabled = 0, pin_hash = NULL, version = 0, updated_at = NULL WHERE id = 1");
    }

    private OnSitePaymentDto.SettingUpdateRequest update(Boolean enabled, String pin) {
        OnSitePaymentDto.SettingUpdateRequest req = mock(OnSitePaymentDto.SettingUpdateRequest.class);
        when(req.getEnabled()).thenReturn(enabled);
        when(req.getPin()).thenReturn(pin);
        return req;
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((BusinessException) t).getErrorCode();
    }

    @Test
    @DisplayName("처음에는 꺼져 있어 PIN 을 넣어도 들어갈 수 없다")
    void disabledByDefault() {
        assertThat(onSiteAccessService.status().isEnabled()).isFalse();
        assertThatThrownBy(() -> onSiteAccessService.openSession(PIN))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ONSITE_DISABLED));
    }

    @Test
    @DisplayName("PIN 없이 켜려고 하면 막는다")
    void cannotEnableWithoutPin() {
        assertThatThrownBy(() -> onSiteAccessService.updateSetting(update(true, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(onSiteAccessService.getSetting().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("켜고 PIN 이 맞으면 입장권을 주고, 그 입장권으로 결제를 만들 수 있다")
    void opensSessionWithRightPin() {
        OnSitePaymentDto.SettingResponse setting = onSiteAccessService.updateSetting(update(true, PIN));
        assertThat(setting.isEnabled()).isTrue();
        assertThat(setting.isPinSet()).isTrue();

        String token = onSiteAccessService.openSession(PIN).getSessionToken();

        assertThatCode(() -> onSiteAccessService.verifySession(token)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PIN 이 틀리면 입장권을 주지 않는다")
    void rejectsWrongPin() {
        onSiteAccessService.updateSetting(update(true, PIN));

        assertThatThrownBy(() -> onSiteAccessService.openSession("000000"))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ONSITE_PIN_INVALID));
    }

    @Test
    @DisplayName("끄면 이미 받은 입장권도 바로 막힌다")
    void disablingRevokesSessions() {
        onSiteAccessService.updateSetting(update(true, PIN));
        String token = onSiteAccessService.openSession(PIN).getSessionToken();

        onSiteAccessService.updateSetting(update(false, null));

        assertThatThrownBy(() -> onSiteAccessService.verifySession(token))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ONSITE_DISABLED));
    }

    @Test
    @DisplayName("PIN 을 바꾸면 이전 입장권은 다시 켜져 있어도 쓸 수 없다")
    void changingPinRevokesSessions() {
        onSiteAccessService.updateSetting(update(true, PIN));
        String token = onSiteAccessService.openSession(PIN).getSessionToken();

        onSiteAccessService.updateSetting(update(null, "135790"));

        assertThatThrownBy(() -> onSiteAccessService.verifySession(token))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ONSITE_SESSION_EXPIRED));
        assertThat(onSiteAccessService.openSession("135790").getSessionToken()).isNotBlank();
    }

    @Test
    @DisplayName("서명을 고친 입장권은 받지 않는다")
    void rejectsForgedToken() {
        onSiteAccessService.updateSetting(update(true, PIN));
        String token = onSiteAccessService.openSession(PIN).getSessionToken();
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + (Long.parseLong(parts[1]) + 86_400) + "." + parts[2];

        assertThatThrownBy(() -> onSiteAccessService.verifySession(forged))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ONSITE_SESSION_EXPIRED));
        assertThatThrownBy(() -> onSiteAccessService.verifySession(null))
                .satisfies(t -> assertThat(codeOf(t)).isEqualTo(ErrorCode.ONSITE_SESSION_EXPIRED));
    }
}
