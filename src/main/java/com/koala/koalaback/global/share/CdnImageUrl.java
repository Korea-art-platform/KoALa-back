package com.koala.koalaback.global.share;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예전에 올라간 이미지는 주소가 S3 버킷으로 저장돼 있다. 지금 올리는 것은 CDN 주소로 저장된다.
 * 미리보기 태그에는 CDN 주소로 통일해 내보낸다 — 버킷을 잠그더라도 미리보기가 살아 있어야 한다.
 */
public final class CdnImageUrl {
    private static final Pattern S3_ORIGIN =
            Pattern.compile("^https?://[A-Za-z0-9.\\-]+\\.s3[.\\-][A-Za-z0-9\\-]*\\.?amazonaws\\.com");

    private CdnImageUrl() {
    }

    public static String toCdn(String url, String cdnBaseUrl) {
        if (url == null || url.isBlank()) return url;
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) return url;

        String base = cdnBaseUrl.endsWith("/") ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1) : cdnBaseUrl;
        Matcher matcher = S3_ORIGIN.matcher(url);
        if (!matcher.find()) return url;

        return matcher.replaceFirst(Matcher.quoteReplacement(base));
    }
}
