package com.koala.koalaback.infra.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운송장 조회 응답 해석.
 *
 * <p>여기서 틀리면 <b>배송완료를 놓치거나 잘못 찍는다.</b> 잘못 찍으면 고객은 받지도 않은
 * 물건에 대해 반품 기한이 돌기 시작하고, 놓치면 반품 신청 자체를 할 수 없다.
 *
 * <p>특히 "조회 실패"와 "배송 안 됨"을 섞지 않는 것이 핵심이다.
 */
@DisplayName("운송장 조회 응답 해석")
class SweetTrackerDeliveryTrackerTest {

    private final SweetTrackerDeliveryTracker tracker =
            new SweetTrackerDeliveryTracker(new ObjectMapper(), "dummy-key", 5000);

    @Test
    @DisplayName("level 6 은 배송완료")
    void level6IsDelivered() {
        assertThat(tracker.parse("{\"status\":true,\"level\":6}"))
                .isEqualTo(DeliveryTracker.Status.DELIVERED);
    }

    @Test
    @DisplayName("complete=true 도 배송완료 — level 을 안 주는 응답이 있다")
    void completeFlagIsDelivered() {
        assertThat(tracker.parse("{\"status\":true,\"complete\":true,\"level\":4}"))
                .isEqualTo(DeliveryTracker.Status.DELIVERED);
    }

    @Test
    @DisplayName("배송 중 단계는 IN_TRANSIT")
    void inTransit() {
        assertThat(tracker.parse("{\"status\":true,\"level\":3,\"complete\":false}"))
                .isEqualTo(DeliveryTracker.Status.IN_TRANSIT);
    }

    @Test
    @DisplayName("status=false 는 UNKNOWN — 미등록 운송장·키 오류가 여기로 온다")
    void errorResponseIsUnknown() {
        assertThat(tracker.parse("{\"status\":false,\"msg\":\"운송장 번호가 존재하지 않습니다\"}"))
                .isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("level 이 없으면 UNKNOWN — 배송 중이라고 단정하지 않는다")
    void missingLevelIsUnknown() {
        assertThat(tracker.parse("{\"status\":true}"))
                .isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("응답이 JSON 이 아니어도 예외 없이 UNKNOWN")
    void malformedIsUnknown() {
        assertThat(tracker.parse("<html>503 Service Unavailable</html>"))
                .isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("빈 응답도 UNKNOWN")
    void blankIsUnknown() {
        assertThat(tracker.parse("")).isEqualTo(DeliveryTracker.Status.UNKNOWN);
        assertThat(tracker.parse(null)).isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("지원하지 않는 택배사는 호출조차 하지 않고 UNKNOWN")
    void unsupportedCarrierIsUnknown() {
        // 예전 자유 입력으로 저장된 값 — 코드가 아니라 이름이 들어 있다
        assertThat(tracker.track("CJ대한통운", "123456789"))
                .isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("운송장 번호가 비면 UNKNOWN")
    void blankTrackingNoIsUnknown() {
        assertThat(tracker.track("04", "")).isEqualTo(DeliveryTracker.Status.UNKNOWN);
        assertThat(tracker.track("04", null)).isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("키가 없으면 조회하지 않는다")
    void noApiKeyIsUnknown() {
        SweetTrackerDeliveryTracker noKey =
                new SweetTrackerDeliveryTracker(new ObjectMapper(), "", 5000);

        assertThat(noKey.track("04", "123456789")).isEqualTo(DeliveryTracker.Status.UNKNOWN);
    }

    @Test
    @DisplayName("택배사 코드는 목록에 있는 것만 인식한다")
    void carrierLookup() {
        assertThat(Carrier.fromCode("04")).contains(Carrier.CJ);
        assertThat(Carrier.fromCode("99")).isEmpty();
        assertThat(Carrier.fromCode(null)).isEmpty();
    }
}
