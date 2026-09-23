package com.koala.koalaback.global.share;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("링크 미리보기 HTML")
class ShareMetaHtmlTest {

    @Test
    @DisplayName("제목·설명·이미지·주소를 미리보기 태그에 넣는다")
    void fillsTags() {
        String html = ShareMetaHtml.render("작품 이름", "작품 설명",
                "https://cdn.example.com/a.jpg", "https://koala-art.co.kr/product/ABC", "product");

        assertThat(html)
                .contains("<title>작품 이름</title>")
                .contains("property=\"og:title\" content=\"작품 이름\"")
                .contains("property=\"og:description\" content=\"작품 설명\"")
                .contains("property=\"og:image\" content=\"https://cdn.example.com/a.jpg\"")
                .contains("property=\"og:url\" content=\"https://koala-art.co.kr/product/ABC\"")
                .contains("property=\"og:type\" content=\"product\"")
                .contains("name=\"twitter:card\" content=\"summary_large_image\"");
    }

    @Test
    @DisplayName("사람이 열면 원래 화면으로 보낸다")
    void sendsPeopleOn() {
        String html = ShareMetaHtml.render("작품", "설명", "https://x/y.jpg",
                "https://koala-art.co.kr/product/ABC", "product");

        assertThat(html)
                .contains("http-equiv=\"refresh\" content=\"0; url=https://koala-art.co.kr/product/ABC\"")
                .contains("<a href=\"https://koala-art.co.kr/product/ABC\">");
    }

    @Test
    @DisplayName("따옴표와 꺾쇠가 섞인 값이 태그를 깨뜨리지 않는다")
    void escapesDangerousText() {
        String html = ShareMetaHtml.render("<script>alert(1)</script>", "\"큰따옴표\" & 'a'",
                "https://x/y.jpg?a=1&b=2", "https://koala-art.co.kr/product/A\"B", "product");

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .contains("&quot;큰따옴표&quot; &amp; &#39;a&#39;")
                .contains("y.jpg?a=1&amp;b=2");
    }

    @Test
    @DisplayName("긴 설명은 잘라서 줄바꿈 없이 한 줄로 만든다")
    void clampsLongDescription() {
        String long1 = "가".repeat(400);
        String html = ShareMetaHtml.render("제목", "앞줄\n\n  뒷줄 " + long1, "https://x/y.jpg",
                "https://koala-art.co.kr/", "website");

        String description = html.split("name=\"description\" content=\"")[1].split("\"")[0];
        assertThat(description).hasSizeLessThanOrEqualTo(150).doesNotContain("\n").startsWith("앞줄 뒷줄").endsWith("…");
    }

    @Test
    @DisplayName("값이 없어도 태그는 비어 있는 채로 남는다")
    void handlesNulls() {
        String html = ShareMetaHtml.render("제목", null, null, "https://koala-art.co.kr/", "website");

        assertThat(html).contains("property=\"og:description\" content=\"\"")
                .contains("property=\"og:image\" content=\"\"");
    }
}
