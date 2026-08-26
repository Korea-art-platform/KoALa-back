package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("스토어 분류 거르기")
class SkuFilterIntegrationTest extends IntegrationTestSupport {

    // 다른 테스트가 남긴 작품과 섞이면 개수가 흔들린다. 이 테스트만 쓰는 코드를 둔다.
    private static final String ORIGINAL = "FTEST_ORIGINAL";
    private static final String NORMAL = "FTEST_NORMAL";
    private static final String SCULPTURE = "FTEST_SCULPTURE";
    private static final String ART_TOY = "FTEST_ART_TOY";

    @Autowired private SkuService skuService;
    @Autowired private SkuRepository skuRepository;
    @Autowired private ArtistRepository artistRepository;

    private String uid;

    @BeforeEach
    void setUp() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        Artist artist = artistRepository.save(Artist.builder()
                .artistCode("FTEST-" + uid)
                .name("거르기 작가")
                .slug("ftest-artist-" + uid)
                .build());

        // 원작 4점(조각 3 · 아트토이 1) + 일반 2점. 한 페이지(3개)를 넘기게 두어
        // 거르기가 페이지를 자르기 "전에" 일어나는지 볼 수 있게 한다.
        save(artist, ORIGINAL, SCULPTURE);
        save(artist, ORIGINAL, SCULPTURE);
        save(artist, ORIGINAL, SCULPTURE);
        save(artist, ORIGINAL, ART_TOY);
        save(artist, NORMAL, SCULPTURE);
        save(artist, NORMAL, ART_TOY);
    }

    private void save(Artist artist, String mainCategory, String genre) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Sku sku = Sku.builder()
                .skuCode("FTEST-" + id)
                .artist(artist)
                .name("거르기용 작품")
                .slug("ftest-sku-" + id)
                .skuType("ARTWORK")
                .mainCategory(mainCategory)
                .genre(genre)
                .currency("KRW")
                .listPrice(BigDecimal.valueOf(10_000))
                .build();
        sku.publish();
        skuRepository.save(sku);
    }

    private List<SkuDto.SummaryResponse> page(String genre, String mainCategory) {
        PageResponse<SkuDto.SummaryResponse> res =
                skuService.getActiveSkus(genre, mainCategory, PageRequest.of(0, 3));
        return res.getContent();
    }

    private long total(String genre, String mainCategory) {
        return skuService.getActiveSkus(genre, mainCategory, PageRequest.of(0, 3))
                .getTotalElements();
    }

    @Test
    @DisplayName("대분류로 거르면 그 대분류만 나온다")
    void byMainCategory() {
        assertThat(total(null, ORIGINAL)).isEqualTo(4);
        assertThat(page(null, ORIGINAL))
                .allSatisfy(s -> assertThat(s.getMainCategory()).isEqualTo(ORIGINAL));
    }

    @Test
    @DisplayName("소분류로 거르면 그 소분류만 나온다")
    void byGenre() {
        assertThat(total(ART_TOY, null)).isEqualTo(2);
        assertThat(page(ART_TOY, null))
                .allSatisfy(s -> assertThat(s.getGenre()).isEqualTo(ART_TOY));
    }

    @Test
    @DisplayName("대분류와 소분류를 함께 걸면 둘 다 맞는 것만 나온다")
    void byBoth() {
        assertThat(total(SCULPTURE, ORIGINAL)).isEqualTo(3);
        assertThat(page(SCULPTURE, ORIGINAL)).allSatisfy(s -> {
            assertThat(s.getMainCategory()).isEqualTo(ORIGINAL);
            assertThat(s.getGenre()).isEqualTo(SCULPTURE);
        });
    }

    @Test
    @DisplayName("전체 개수는 페이지 크기가 아니라 거른 결과를 센다")
    void countsFilteredNotPaged() {
        // 원작 4점인데 한 페이지는 3개다. 화면에서 걸렀다면 3 이 나왔을 자리다.
        PageResponse<SkuDto.SummaryResponse> res =
                skuService.getActiveSkus(null, ORIGINAL, PageRequest.of(0, 3));
        assertThat(res.getContent()).hasSize(3);
        assertThat(res.getTotalElements()).isEqualTo(4);
        assertThat(res.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("빈 문자열은 거르지 않는 것으로 본다")
    void blankMeansNoFilter() {
        // 스토어에서 "전체"를 고르면 파라미터가 빈 문자열로 올 수 있다.
        assertThat(total("", ORIGINAL)).isEqualTo(total(null, ORIGINAL));
        assertThat(total(SCULPTURE, "")).isEqualTo(total(SCULPTURE, null));
    }
}
