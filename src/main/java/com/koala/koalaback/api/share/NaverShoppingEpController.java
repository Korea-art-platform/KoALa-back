package com.koala.koalaback.api.share;

import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.repository.SkuCategoryRepository;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuMedia;
import com.koala.koalaback.domain.sku.repository.SkuMediaRepository;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.share.CdnImageUrl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Tag(name = "네이버 쇼핑 EP", description = "네이버 쇼핑이 매일 가져가는 상품 목록")
@RestController
@RequiredArgsConstructor
public class NaverShoppingEpController {
    static final String[] COLUMNS = {
            "id", "title", "price_pc", "normal_price", "link", "mobile_link", "image_link", "add_image_link",
            "category_name1", "category_name2", "brand", "maker", "origin", "condition", "shipping",
            "search_tag", "update_time"
    };
    static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50000");
    static final int SHIPPING_FEE = 3000;
    private static final int MAX_ITEMS = 5000;
    private static final int MAX_EXTRA_IMAGES = 10;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String TRACKING = "?utm_source=naver&utm_medium=shopping&utm_campaign=ep";

    private final SkuRepository skuRepository;
    private final SkuMediaRepository skuMediaRepository;
    private final SkuCategoryRepository categoryRepository;
    private final VatPolicy vatPolicy;

    @Value("${koala.web-base-url:https://koala-art.co.kr}")
    private String webBaseUrl;

    @Value("${koala.cdn-base-url:}")
    private String cdnBaseUrl;

    @Operation(summary = "네이버 쇼핑 상품 EP(전체)", description = """
            네이버 쇼핑파트너센터에 등록하는 상품 DB URL. EP 3.0 형식(첫 줄 헤더, 탭 구분, UTF-8)으로
            판매 중인 작품 전체를 돌려준다. 가격은 화면과 같은 부가세 포함 판매가다.
            """)
    @GetMapping(value = "/api/v1/feeds/naver-ep.txt")
    @Transactional(readOnly = true)
    public ResponseEntity<String> naverEp() {
        Map<String, String> categoryNames = categoryRepository.findAllByOrderByTypeAscSortOrderAsc().stream()
                .collect(Collectors.toMap(c -> c.getType() + ":" + c.getCode(), SkuCategory::getName, (a, b) -> a));
        Set<String> exempt = vatPolicy.exemptMainCategories();

        List<Sku> skus = skuRepository
                .findByStatusAndDeletedAtIsNull("ACTIVE", PageRequest.of(0, MAX_ITEMS))
                .getContent();

        StringBuilder ep = new StringBuilder(String.join("\t", COLUMNS)).append('\n');
        for (Sku sku : skus) {
            BigDecimal price = vatPolicy.grossOf(sku.getEffectivePrice(), sku.getMainCategory(), exempt);
            BigDecimal listPrice = vatPolicy.grossOf(sku.getListPrice(), sku.getMainCategory(), exempt);
            if (price == null || price.signum() <= 0 || isBlank(sku.getPrimaryImageUrl())) continue;

            List<String> extraImages = skuMediaRepository.findBySkuIdOrderByMediaRoleAscSortOrderAsc(sku.getId()).stream()
                    .filter(m -> "IMAGE".equals(m.getMediaType()) && "DETAIL".equals(m.getMediaRole()))
                    .map(SkuMedia::getFileUrl)
                    .filter(Objects::nonNull)
                    .filter(u -> !u.equals(sku.getPrimaryImageUrl()))
                    .map(this::image)
                    .limit(MAX_EXTRA_IMAGES)
                    .toList();

            String link = webBaseUrl + "/product/" + sku.getSkuCode() + TRACKING;
            String artist = sku.getArtist() != null ? sku.getArtist().getName() : "";
            boolean free = sku.isFreeShipping() || price.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;

            Function<String, String> mainName = code -> categoryNames.getOrDefault("MAIN:" + code, "");
            Function<String, String> subName = code -> categoryNames.getOrDefault("SUB:" + code, "");

            row(ep,
                    sku.getSkuCode(),
                    title(sku, artist),
                    won(price),
                    listPrice != null && listPrice.compareTo(price) > 0 ? won(listPrice) : "",
                    link,
                    link,
                    image(sku.getPrimaryImageUrl()),
                    String.join("|", extraImages),
                    firstNonBlank(mainName.apply(sku.getMainCategory()), "아트토이"),
                    subName.apply(sku.getGenre()),
                    artist,
                    artist,
                    "국내",
                    "신상품",
                    free ? "0" : String.valueOf(SHIPPING_FEE),
                    searchTags(sku, artist),
                    (sku.getUpdatedAt() == null ? LocalDateTime.now(SEOUL) : sku.getUpdatedAt()).format(TIME));
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(ep.toString());
    }

    private String title(Sku sku, String artist) {
        String name = sku.getName() == null ? "" : sku.getName();
        return artist.isBlank() || name.contains(artist) ? name : artist + " " + name;
    }

    private String searchTags(Sku sku, String artist) {
        return Stream.of(artist, sku.getModel(), "아트토이", "KOALA")
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.joining("|"));
    }

    private String image(String url) {
        return CdnImageUrl.toCdn(url, cdnBaseUrl);
    }

    private static String won(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static void row(StringBuilder out, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append('\t');
            out.append(clean(values[i]));
        }
        out.append('\n');
    }

    static String clean(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\t\\r\\n\\p{Cntrl}]+", " ").trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }
}
