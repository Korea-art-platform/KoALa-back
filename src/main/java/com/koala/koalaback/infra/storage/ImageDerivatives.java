package com.koala.koalaback.infra.storage;

/**
 * 이미지 파생본(축소본) 규칙.
 *
 * 프론트도 같은 규칙으로 주소를 만든다(Koalaweb 의 imageUrl.ts). 한쪽만
 * 바꾸면 축소본을 못 찾고 원본으로 되돌아가므로 양쪽을 같이 고쳐야 한다.
 */
public final class ImageDerivatives {
    private ImageDerivatives() {}

    /** 축소본의 긴 변 길이. 카드가 240px 안팎이라 2배 화면까지 덮는다. */
    public static final int THUMB_EDGE = 480;

    /** 원본 키의 확장자 앞에 붙는다. main/abc.jpg → main/abc_t480.jpg */
    public static final String THUMB_SUFFIX = "_t" + THUMB_EDGE;

    /**
     * 이미지는 내용이 바뀌면 키(UUID)도 바뀐다. 그래서 영구 캐시로 둘 수 있다.
     * 지금까지 이 헤더가 없어 재방문마다 8MB 를 다시 받고 있었다.
     */
    public static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    public static String thumbKey(String key) {
        if (key == null || key.isBlank()) return key;
        if (isThumbKey(key)) return key;

        int dot = key.lastIndexOf('.');
        int slash = key.lastIndexOf('/');
        if (dot <= slash) return key + THUMB_SUFFIX;
        return key.substring(0, dot) + THUMB_SUFFIX + key.substring(dot);
    }

    public static boolean isThumbKey(String key) {
        if (key == null) return false;
        int dot = key.lastIndexOf('.');
        int slash = key.lastIndexOf('/');
        String base = dot > slash ? key.substring(0, dot) : key;
        return base.endsWith(THUMB_SUFFIX);
    }
}
