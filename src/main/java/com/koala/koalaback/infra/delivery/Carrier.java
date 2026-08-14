package com.koala.koalaback.infra.delivery;

import java.util.Arrays;
import java.util.Optional;

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

    public static Optional<Carrier> fromCode(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(c -> c.code.equals(code))
                .findFirst();
    }
}
