package com.koala.koalaback.global.share;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("미리보기 이미지 주소")
class CdnImageUrlTest {
    private static final String CDN = "https://d21ujaa1y1qwaz.cloudfront.net";

    @Test
    @DisplayName("예전 S3 주소는 CDN 주소로 바꾼다")
    void rewritesS3() {
        assertThat(CdnImageUrl.toCdn(
                "https://koala-media-bucket.s3.ap-southeast-2.amazonaws.com/skus/A/main/b.jpg", CDN))
                .isEqualTo(CDN + "/skus/A/main/b.jpg");
    }

    @Test
    @DisplayName("이미 CDN 주소면 그대로 둔다")
    void keepsCdn() {
        String url = CDN + "/skus/A/main/b.jpg";
        assertThat(CdnImageUrl.toCdn(url, CDN)).isEqualTo(url);
    }

    @Test
    @DisplayName("CDN 설정이 없으면 건드리지 않는다")
    void keepsWhenUnset() {
        String url = "https://koala-media-bucket.s3.ap-southeast-2.amazonaws.com/a.jpg";
        assertThat(CdnImageUrl.toCdn(url, "")).isEqualTo(url);
        assertThat(CdnImageUrl.toCdn(url, null)).isEqualTo(url);
    }

    @Test
    @DisplayName("끝에 빗금이 붙은 설정이어도 주소가 겹치지 않는다")
    void trimsTrailingSlash() {
        assertThat(CdnImageUrl.toCdn(
                "https://koala-media-bucket.s3.ap-southeast-2.amazonaws.com/a.jpg", CDN + "/"))
                .isEqualTo(CDN + "/a.jpg");
    }

    @Test
    @DisplayName("빈 값이나 다른 주소는 그대로 둔다")
    void keepsOthers() {
        assertThat(CdnImageUrl.toCdn(null, CDN)).isNull();
        assertThat(CdnImageUrl.toCdn("https://example.com/a.jpg", CDN)).isEqualTo("https://example.com/a.jpg");
    }
}
