package com.koala.koalaback.api.share;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.support.IntegrationTestSupport;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@DisplayName("네이버 쇼핑 EP")
class NaverShoppingEpControllerTest extends IntegrationTestSupport {
    @Autowired private WebApplicationContext context;
    @Autowired private Filter springSecurityFilterChain;
    @Autowired private SkuRepository skuRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM sku_media");
        jdbcTemplate.update("DELETE FROM skus");
        jdbcTemplate.update("DELETE FROM artists");
    }

    @Test
    @DisplayName("로그인 없이 받을 수 있고, 첫 줄은 헤더·나머지는 판매 중인 작품만 탭으로 구분한다")
    void listsActiveProductsAsTsv() throws Exception {
        Sku cheap = sku("버즈 아기매기", "1000", true);
        Sku pricey = sku("호돌이 원작", "100000", true);
        sku("미공개 작품", "50000", false);

        MockHttpServletResponse res = mvc.perform(get("/api/v1/feeds/naver-ep.txt")).andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentType()).startsWith("text/plain");
        List<String> lines = Arrays.asList(res.getContentAsString(StandardCharsets.UTF_8).split("\n"));
        List<String> header = Arrays.asList(lines.get(0).split("\t", -1));
        assertThat(header).startsWith("id", "title", "price_pc").contains("link", "image_link", "category_name1", "shipping");
        assertThat(lines).hasSize(3);

        for (String line : lines.subList(1, lines.size())) {
            assertThat(line.split("\t", -1)).as("모든 줄의 칸 수가 헤더와 같아야 한다").hasSize(header.size());
        }

        String cheapRow = lines.stream().filter(l -> l.startsWith(cheap.getSkuCode() + "\t")).findFirst().orElseThrow();
        String[] c = cheapRow.split("\t", -1);
        assertThat(c[header.indexOf("price_pc")]).as("부가세 포함 판매가").isEqualTo("1100");
        assertThat(c[header.indexOf("shipping")]).isEqualTo("3000");
        assertThat(c[header.indexOf("link")]).startsWith("https://koala-art.co.kr/product/" + cheap.getSkuCode());

        String priceyRow = lines.stream().filter(l -> l.startsWith(pricey.getSkuCode() + "\t")).findFirst().orElseThrow();
        assertThat(priceyRow.split("\t", -1)[header.indexOf("shipping")]).as("5만원 이상은 무료배송").isEqualTo("0");
    }

    @Test
    @DisplayName("상품명에 탭·줄바꿈이 섞여도 칸이 밀리지 않는다")
    void cleansControlCharacters() {
        assertThat(NaverShoppingEpController.clean("작품\t이름\n두줄")).isEqualTo("작품 이름 두줄");
    }

    private Sku sku(String name, String supplyPrice, boolean active) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Artist artist = artistRepository.save(Artist.builder()
                .artistCode("EP-" + uid).name("박준상").slug("ep-artist-" + uid).build());
        Sku sku = Sku.builder()
                .skuCode("EP" + uid.toUpperCase())
                .artist(artist)
                .name(name)
                .slug("ep-sku-" + uid)
                .skuType("ARTWORK")
                .mainCategory(Sku.MAIN_NORMAL)
                .genre("ART_TOY")
                .currency("KRW")
                .listPrice(new BigDecimal(supplyPrice))
                .primaryImageUrl("https://cdn.example.com/skus/" + uid + "/main.jpg")
                .build();
        if (active) sku.publish();
        return skuRepository.save(sku);
    }
}
