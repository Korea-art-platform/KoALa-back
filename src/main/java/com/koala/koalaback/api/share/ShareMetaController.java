package com.koala.koalaback.api.share;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.global.share.CdnImageUrl;
import com.koala.koalaback.global.share.ShareMetaHtml;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "링크 미리보기", description = "카카오톡·페이스북 봇이 읽는 자리")
@Slf4j
@RestController
@RequiredArgsConstructor
public class ShareMetaController {
    private static final String DEFAULT_TITLE = "KOALA — Korea Art Lab";
    private static final String DEFAULT_DESCRIPTION =
            "한국 작가의 작품을 발견하고 소장하세요. 원작부터 한정판·오픈에디션까지 KOALA에서 만나보세요.";
    private static final String SELLER = "주식회사 헤론";

    private final SkuService skuService;
    private final ArtistService artistService;
    private final ObjectMapper objectMapper;

    @Value("${koala.web-base-url:https://koala-art.co.kr}")
    private String webBaseUrl;

    @Value("${koala.cdn-base-url:}")
    private String cdnBaseUrl;

    @Operation(summary = "작품 미리보기", description = """
            작품 주소를 공유했을 때 그 작품의 제목·설명·이미지가 뜨게 한다.
            없는 작품이면 기본값으로 돌려준다 — 봇에게 404 를 주면 미리보기가 통째로 사라진다.
            """)
    @GetMapping(value = "/api/v1/share/product/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> product(@PathVariable String code) {
        String canonical = webBaseUrl + "/product/" + code;
        try {
            SkuDto.DetailResponse sku = skuService.getSkuByCode(code);
            String title = sku.getArtistName() == null
                    ? sku.getName()
                    : sku.getName() + " — " + sku.getArtistName();
            String description = sku.getDescription() == null || sku.getDescription().isBlank()
                    ? sku.getName() + " · KOALA에서 소장하세요."
                    : sku.getDescription();
            return html(ShareMetaHtml.render(title, description, image(sku.getPrimaryImageUrl()), canonical,
                    "product", productJsonLd(sku, canonical)));
        } catch (RuntimeException e) {
            log.info("미리보기 대상 작품을 찾지 못했다: code={}", code);
            return html(defaultHtml(canonical));
        }
    }

    @Operation(summary = "작가 미리보기", description = "작가 주소를 공유했을 때 그 작가의 이름과 사진이 뜨게 한다.")
    @GetMapping(value = "/api/v1/share/artist/{code}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> artist(@PathVariable String code) {
        String canonical = webBaseUrl + "/artist/" + code;
        try {
            ArtistDto.DetailResponse artist = artistService.getArtist(code, null);
            String description = artist.getDescription() == null || artist.getDescription().isBlank()
                    ? artist.getName() + " 작가의 작품을 KOALA에서 만나보세요."
                    : artist.getDescription();
            return html(ShareMetaHtml.render(artist.getName() + " — KOALA",
                    description, image(artist.getProfileImageUrl()), canonical, "profile",
                    artistJsonLd(artist, canonical)));
        } catch (RuntimeException e) {
            log.info("미리보기 대상 작가를 찾지 못했다: code={}", code);
            return html(defaultHtml(canonical));
        }
    }

    @Operation(summary = "그 밖의 화면 미리보기", description = "작품·작가가 아닌 주소는 기본 소개로 돌려준다.")
    @GetMapping(value = "/api/v1/share", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> site() {
        return html(defaultHtml(webBaseUrl));
    }

    /** 검색엔진과 생성형 크롤러가 읽는 구조화 데이터. 값이 없는 항목은 넣지 않는다. */
    private String productJsonLd(SkuDto.DetailResponse sku, String canonical) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("@context", "https://schema.org");
        json.put("@type", "Product");
        json.put("name", sku.getName());
        json.put("url", canonical);
        json.put("sku", sku.getSkuCode());

        if (sku.getPrimaryImageUrl() != null) {
            json.put("image", image(sku.getPrimaryImageUrl()));
        }
        if (sku.getDescription() != null && !sku.getDescription().isBlank()) {
            json.put("description", flatten(sku.getDescription()));
        }
        if (sku.getArtistName() != null) {
            json.put("brand", Map.of("@type", "Brand", "name", sku.getArtistName()));
        }
        if (sku.getSalePrice() != null) {
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("@type", "Offer");
            offer.put("priceCurrency", "KRW");
            offer.put("price", sku.getSalePrice().stripTrailingZeros().toPlainString());
            offer.put("availability", "https://schema.org/"
                    + ("ACTIVE".equals(sku.getStatus()) ? "InStock" : "OutOfStock"));
            offer.put("url", canonical);
            offer.put("seller", Map.of("@type", "Organization", "name", SELLER));
            json.put("offers", offer);
        }
        return write(json);
    }

    private String artistJsonLd(ArtistDto.DetailResponse artist, String canonical) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("@context", "https://schema.org");
        json.put("@type", "Person");
        json.put("name", artist.getName());
        json.put("url", canonical);
        if (artist.getProfileImageUrl() != null) {
            json.put("image", image(artist.getProfileImageUrl()));
        }
        if (artist.getDescription() != null && !artist.getDescription().isBlank()) {
            json.put("description", flatten(artist.getDescription()));
        }
        return write(json);
    }

    /**
     * script 안에 들어가므로 여는 꺾쇠를 이스케이프한다. 그대로 두면 문서에 &lt;/script&gt; 가
     * 섞여 들어와 구조화 데이터가 통째로 버려진다.
     */
    private String write(Map<String, Object> json) {
        try {
            return objectMapper.writeValueAsString(json).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            log.warn("구조화 데이터를 만들지 못했다", e);
            return null;
        }
    }

    private String flatten(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String defaultHtml(String canonical) {
        return ShareMetaHtml.render(DEFAULT_TITLE, DEFAULT_DESCRIPTION, defaultImage(), canonical, "website");
    }

    private String image(String url) {
        return url == null || url.isBlank() ? defaultImage() : CdnImageUrl.toCdn(url, cdnBaseUrl);
    }

    private String defaultImage() {
        return webBaseUrl + "/og-image.png";
    }

    private ResponseEntity<String> html(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(body);
    }
}
