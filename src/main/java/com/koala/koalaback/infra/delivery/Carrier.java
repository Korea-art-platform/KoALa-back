package com.koala.koalaback.infra.delivery;

import java.util.Arrays;
import java.util.Optional;

/**
 * 지원 택배사.
 *
 * <p>지금까지 택배사는 어드민에서 <b>자유 입력</b>이었다("CJ대한통운" 처럼 사람이 타이핑).
 * 사람이 읽기에는 문제가 없지만 조회 API 는 코드를 요구하므로, 자유 입력인 채로는
 * 자동 추적을 붙일 수 없다. 그래서 목록을 고정한다.
 *
 * <p>{@code code} 는 조회 API(스윗트래커)가 쓰는 값이다. 화면에는 {@code displayName} 만 보인다.
 *
 * <p>목록에 없는 택배사로 보내야 하면 운송장은 그대로 등록되고 자동 추적만 되지 않는다 —
 * 배송완료를 손으로 눌러 주면 된다. 추적하지 못한다고 발송 자체를 막지는 않는다.
 */
public enum Carrier {

    KOREA_POST("01", "우체국택배"),
    CJ("04", "CJ대한통운"),
    HANJIN("05", "한진택배"),
    LOGEN("06", "로젠택배"),
    LOTTE("08", "롯데택배");

    private final String code;
    private final String displayName;

    Carrier(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    /**
     * 코드로 찾는다.
     *
     * <p>예전에 자유 입력으로 저장된 값("CJ대한통운")은 여기서 걸리지 않는다.
     * 그 건들은 자동 추적 대상에서 조용히 빠진다 — 손으로 처리하던 그대로다.
     */
    public static Optional<Carrier> fromCode(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(c -> c.code.equals(code))
                .findFirst();
    }
}
