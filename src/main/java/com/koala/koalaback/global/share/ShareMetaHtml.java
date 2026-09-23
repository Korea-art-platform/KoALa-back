package com.koala.koalaback.global.share;

/**
 * 링크 미리보기용 최소 HTML.
 *
 * 카카오톡·페이스북·네이버의 미리보기 봇은 자바스크립트를 실행하지 않는다. 우리 화면은
 * 떠 있는 뒤에야 제목과 이미지를 바꿔 끼우므로, 봇에게는 어떤 주소를 보내도 같은 기본값만
 * 보인다. 그래서 봇 요청만 이 자리로 돌려 필요한 태그를 미리 박아 돌려준다.
 *
 * 사람이 이 주소로 들어오는 경우(봇 판별이 어긋났을 때)를 대비해 원래 화면으로 보낸다.
 */
public final class ShareMetaHtml {
    private static final int DESCRIPTION_LIMIT = 150;

    private ShareMetaHtml() {
    }

    public static String render(String title, String description, String imageUrl, String canonicalUrl, String type) {
        String safeTitle = escape(title);
        String safeDescription = escape(clamp(description));
        String safeImage = escape(imageUrl);
        String safeUrl = escape(canonicalUrl);
        String safeType = escape(type);

        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <title>%s</title>
                <meta name="description" content="%s">
                <link rel="canonical" href="%s">
                <meta property="og:type" content="%s">
                <meta property="og:site_name" content="KOALA">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:url" content="%s">
                <meta property="og:locale" content="ko_KR">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%s">
                <meta name="twitter:description" content="%s">
                <meta name="twitter:image" content="%s">
                <meta http-equiv="refresh" content="0; url=%s">
                </head>
                <body>
                <a href="%s">%s</a>
                </body>
                </html>
                """.formatted(
                safeTitle, safeDescription, safeUrl, safeType,
                safeTitle, safeDescription, safeImage, safeUrl,
                safeTitle, safeDescription, safeImage,
                safeUrl, safeUrl, safeTitle);
    }

    private static String clamp(String value) {
        if (value == null) return "";
        String flat = value.replaceAll("\\s+", " ").trim();
        if (flat.length() <= DESCRIPTION_LIMIT) return flat;
        return flat.substring(0, DESCRIPTION_LIMIT - 1).trim() + "…";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
