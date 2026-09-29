package com.koala.koalaback.api.share;

import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.notice.repository.NoticeRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 검색엔진에 주소 목록을 준다.
 *
 * 우리 화면은 자바스크립트로 그려지므로 크롤러가 링크를 따라가기 어렵다. 어떤 주소가 있는지
 * 직접 적어 주지 않으면 작품 페이지가 색인되지 않는다. robots.txt 가 이 주소를 가리킨다.
 */
@Tag(name = "사이트맵", description = "검색엔진이 읽는 주소 목록")
@RestController
@RequiredArgsConstructor
public class SitemapController {
    private static final int MAX_ITEMS = 2000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 고정 화면 — 주소, 중요도, 갱신 주기 */
    private static final String[][] STATIC_PAGES = {
            {"", "1.0", "daily"},
            {"/store", "0.9", "daily"},
            {"/artist-lab", "0.8", "weekly"},
            {"/stores", "0.6", "monthly"},
            {"/about", "0.5", "monthly"},
            {"/notice", "0.5", "weekly"},
            {"/faq", "0.4", "monthly"},
            {"/shipping", "0.3", "monthly"},
            {"/returns", "0.3", "monthly"},
            {"/terms", "0.2", "yearly"},
            {"/privacy", "0.2", "yearly"},
    };

    private final SkuRepository skuRepository;
    private final ArtistRepository artistRepository;
    private final NoticeRepository noticeRepository;

    @Value("${koala.web-base-url:https://koala-art.co.kr}")
    private String webBaseUrl;

    @Operation(summary = "사이트맵", description = "고정 화면과 판매 중인 작품·작가·공지 주소를 돌려준다.")
    @GetMapping(value = {"/api/v1/sitemap.xml", "/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        String today = LocalDateTime.now(SEOUL).format(DAY);
        for (String[] page : STATIC_PAGES) {
            append(xml, webBaseUrl + page[0], today, page[1], page[2]);
        }

        List<Sku> skus = skuRepository
                .findByStatusAndDeletedAtIsNull("ACTIVE", PageRequest.of(0, MAX_ITEMS))
                .getContent();
        for (Sku sku : skus) {
            append(xml, webBaseUrl + "/product/" + sku.getSkuCode(), day(sku.getUpdatedAt()), "0.8", "weekly");
        }

        artistRepository.findAll().stream()
                .filter(a -> a.getDeletedAt() == null && Boolean.TRUE.equals(a.getIsActive()))
                .forEach(a -> append(xml, webBaseUrl + "/artist/" + a.getArtistCode(), day(a.getUpdatedAt()), "0.7", "weekly"));

        noticeRepository.findByIsActiveTrueAndDeletedAtIsNullOrderByIsPinnedDescCreatedAtDesc()
                .forEach(n -> append(xml, webBaseUrl + "/notice/" + n.getNoticeCode(), day(n.getUpdatedAt()), "0.4", "monthly"));

        xml.append("</urlset>\n");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(xml.toString());
    }

    private void append(StringBuilder xml, String url, String lastMod, String priority, String changeFreq) {
        xml.append("  <url><loc>").append(escape(url)).append("</loc>")
                .append("<lastmod>").append(lastMod).append("</lastmod>")
                .append("<changefreq>").append(changeFreq).append("</changefreq>")
                .append("<priority>").append(priority).append("</priority></url>\n");
    }

    private String day(LocalDateTime time) {
        return (time == null ? LocalDateTime.now(SEOUL) : time).format(DAY);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
