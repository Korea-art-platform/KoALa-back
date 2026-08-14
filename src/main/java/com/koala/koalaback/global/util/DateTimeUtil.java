package com.koala.koalaback.global.util;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class DateTimeUtil {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private DateTimeUtil() {}

    public static OffsetDateTime nowKst() {
        return OffsetDateTime.now(KST);
    }
}
