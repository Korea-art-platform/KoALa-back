package com.koala.koalaback.api.share;

import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.service.SkuService;
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

@Tag(name = "링크 미리보기", description = "카카오톡·페이스북 봇이 읽는 자리")
@Slf4j
@RestController
@RequiredArgsConstructor
public class ShareMetaController {
    private static final String DEFAULT_TITLE = "KOALA — Korea Art Lab";
    private static final String DEFAULT_DESCRIPTION =
            "한국 작가의 작품을 발견하고 소장하세요. 원작부터 한정판·오픈에디션까지 KOALA에서 만나보세요.";

    private final SkuService skuService;
    private final ArtistService artistService;

    @Value("${koala.web-base-url:https://koala-art.co.kr}")
    private String webBaseUrl;

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
            return html(ShareMetaHtml.render(title, description, image(sku.getPrimaryImageUrl()), canonical, "product"));
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
                    description, image(artist.getProfileImageUrl()), canonical, "profile"));
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

    private String defaultHtml(String canonical) {
        return ShareMetaHtml.render(DEFAULT_TITLE, DEFAULT_DESCRIPTION, defaultImage(), canonical, "website");
    }

    private String image(String url) {
        return url == null || url.isBlank() ? defaultImage() : url;
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
