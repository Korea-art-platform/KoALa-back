package com.koala.koalaback.infra.delivery;

/**
 * 운송장 조회.
 *
 * <p>구현을 갈아 끼울 수 있게 인터페이스로 둔다. 조회 업체는 계약·요금제에 따라 바뀌고,
 * 바뀔 때 스케줄러까지 손대야 할 이유는 없다.
 */
public interface DeliveryTracker {

    /**
     * 배송 상태를 조회한다.
     *
     * <p><b>예외를 던지지 않는다.</b> 네트워크 오류·응답 형식 변경·한도 초과는 전부
     * {@link Status#UNKNOWN} 이다. 조회에 실패했다는 것과 배송이 안 됐다는 것은 다르고,
     * 둘을 섞으면 배송완료를 영영 놓치거나 반대로 잘못 찍는다.
     */
    Status track(String carrierCode, String trackingNo);

    enum Status {
        /** 배송이 끝났다 — 이 값일 때만 주문 상태를 바꾼다 */
        DELIVERED,
        /** 아직 배송 중 */
        IN_TRANSIT,
        /** 알 수 없다 — 조회 실패, 미등록 운송장, 지원하지 않는 택배사 */
        UNKNOWN
    }
}
