package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.support.IntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * csv 일괄 등록 전 과정 통합 테스트 — 파싱 → 사전검증 → 청크 저장.
 *
 * <p>실제 MySQL 을 쓴다. slug UNIQUE·CHECK 제약·트랜잭션 경계가 검증 대상이라
 * H2 나 목으로는 의미가 없다.
 *
 * <p>chunk-size 를 2 로 낮춰 적은 행으로도 여러 청크가 만들어지게 한다.
 */
@TestPropertySource(properties = {
        "koala.csv.chunk-size=2",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@DisplayName("csv 일괄 등록")
class SkuCsvImportIntegrationTest extends IntegrationTestSupport {

    private static final String HEADER = "artistCode,name,slug,listPrice,skuType,genre,"
            + "salePrice,initialStock,isLimitedEdition,editionSize,editionNumber";

    @Autowired private SkuCsvImportService importService;
    @Autowired private SkuRepository skuRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private String artistCode;

    @BeforeEach
    void setUp() {
        artistCode = "CSVTEST-A1";
        artistRepository.save(Artist.builder()
                .artistCode(artistCode)
                .name("csv 테스트 작가")
                .slug("csv-test-artist")
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("""
                DELETE FROM sku_stock_ledger
                WHERE sku_id IN (SELECT id FROM skus WHERE slug LIKE 'csvtest-%')
                """);
        jdbcTemplate.update("""
                DELETE FROM sku_review_stats
                WHERE sku_id IN (SELECT id FROM skus WHERE slug LIKE 'csvtest-%')
                """);
        jdbcTemplate.update("DELETE FROM skus WHERE slug LIKE 'csvtest-%'");
        jdbcTemplate.update("DELETE FROM artists WHERE artist_code LIKE 'CSVTEST-%'");
    }

    @Test
    @DisplayName("정상 5행 — 상품·리뷰집계·재고원장이 모두 저장되고 상태는 DRAFT")
    void validCsv_savesEverything() {
        StringBuilder csv = new StringBuilder(HEADER).append("\n");
        for (int i = 1; i <= 5; i++) {
            csv.append(row("csvtest-ok-" + i, "정상상품" + i, "150000", "10"));
        }

        SkuCsvDto.ImportResult result = importService.importCsv(file(csv.toString()));

        assertThat(result.getTotalRows()).isEqualTo(5);
        assertThat(result.getSucceeded()).isEqualTo(5);
        assertThat(result.getErrors()).isEmpty();

        List<Sku> saved = skuRepository.findAll().stream()
                .filter(s -> s.getSlug().startsWith("csvtest-ok-"))
                .toList();
        assertThat(saved).hasSize(5);
        assertThat(saved).allSatisfy(s -> {
            assertThat(s.getStatus()).as("신규는 DRAFT — 공개 API 는 ACTIVE 만 조회한다").isEqualTo("DRAFT");
            assertThat(s.getSkuCode()).isNotBlank();
        });

        // 리뷰집계 행이 없으면 나중에 리뷰 승인 시 집계가 조용히 반영되지 않는다
        assertThat(countReviewStats()).isEqualTo(5);
        // 재고는 누적 원장 — initialStock 10 이 delta 로 쌓인다
        assertThat(stockOf(saved.get(0).getId())).isEqualTo(10);
    }

    @Test
    @DisplayName("검증 오류가 하나라도 있으면 단 한 건도 저장하지 않는다")
    void anyValidationError_savesNothing() {
        String csv = HEADER + "\n"
                + row("csvtest-good-1", "정상상품", "150000", "5")          // 정상
                + row("csvtest-good-2", "", "150000", "5")                  // name 누락
                + row("CSVTEST-BAD-SLUG", "대문자슬러그", "150000", "5")      // slug 형식 위반
                + row("csvtest-good-3", "정상상품3", "abc", "5")             // 숫자 아님
                + row("csvtest-good-4", "정상상품4", "150000", "-1");        // 재고 음수

        SkuCsvDto.ImportResult result = importService.importCsv(file(csv));

        assertThat(result.getSucceeded()).as("all-or-nothing").isZero();
        assertThat(result.getTotalRows()).isEqualTo(5);
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(countSkus("csvtest-")).as("정상 행도 저장되면 안 된다").isZero();

        assertThat(result.getErrors())
                .extracting(SkuCsvDto.RowError::getField)
                .contains("name", "slug", "listPrice", "initialStock");
    }

    @Test
    @DisplayName("파일 안에서 slug 가 겹치면 양쪽 행을 모두 제외한다")
    void duplicateSlugInFile_rejectsBothRows() {
        String csv = HEADER + "\n"
                + row("csvtest-dup", "먼저 나온 행", "150000", "1")
                + row("csvtest-dup", "나중에 나온 행", "150000", "1");

        SkuCsvDto.ImportResult result = importService.importCsv(file(csv));

        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getErrors())
                .as("어느 쪽이 맞는지 알 수 없으므로 둘 다 뺀다")
                .hasSize(2)
                .allSatisfy(e -> assertThat(e.getReason()).contains("중복"));
    }

    @Test
    @DisplayName("이미 등록된 slug 는 덮어쓰지 않고 오류로 돌린다 — 재고 이중 적립 방지")
    void existingSlug_isRejected() {
        importService.importCsv(file(HEADER + "\n" + row("csvtest-exist", "최초 등록", "150000", "10")));
        assertThat(countSkus("csvtest-exist")).isEqualTo(1);

        // 같은 파일을 다시 올린 상황
        SkuCsvDto.ImportResult result = importService.importCsv(
                file(HEADER + "\n" + row("csvtest-exist", "재업로드", "150000", "10")));

        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getField()).isEqualTo("slug");

        Long skuId = skuRepository.findBySlug("csvtest-exist").orElseThrow().getId();
        assertThat(stockOf(skuId)).as("재고가 20 이 되면 안 된다").isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 작가 코드는 오류로 돌린다")
    void unknownArtistCode_isRejected() {
        String csv = HEADER + "\n"
                + "NO-SUCH-ARTIST,없는작가상품,csvtest-noartist,150000,ARTWORK,ART_TOY,,1,false,,\n";

        SkuCsvDto.ImportResult result = importService.importCsv(file(csv));

        assertThat(result.getSucceeded()).isZero();
        assertThat(result.getErrors().get(0).getField()).isEqualTo("artistCode");
    }

    @Test
    @DisplayName("헤더에 필수 컬럼이 없으면 파일 단위로 거절한다")
    void missingRequiredHeader_throws() {
        String csv = "name,slug,listPrice\n정상상품,csvtest-noheader,150000\n";

        assertThatThrownBy(() -> importService.importCsv(file(csv)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("artistCode"));
    }

    @Test
    @DisplayName("initialStock 이 0 이면 재고 원장에 행을 남기지 않는다")
    void zeroStock_writesNoLedgerRow() {
        importService.importCsv(file(HEADER + "\n" + row("csvtest-zero", "재고없음", "150000", "0")));

        Long skuId = skuRepository.findBySlug("csvtest-zero").orElseThrow().getId();
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sku_stock_ledger WHERE sku_id = ?", Integer.class, skuId);

        assertThat(ledgerRows).isZero();
        assertThat(stockOf(skuId)).as("행이 없어도 합계는 0").isZero();
    }

    @Test
    @DisplayName("1000행 — 검증 쿼리는 행 수와 무관하게 2회")
    void thousandRows_validationUsesTwoQueries() {
        StringBuilder csv = new StringBuilder(HEADER).append("\n");
        for (int i = 1; i <= 1000; i++) {
            csv.append(row("csvtest-bulk-" + i, "대량상품" + i, "150000", "3"));
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        long start = System.currentTimeMillis();
        SkuCsvDto.ImportResult result = importService.importCsv(file(csv.toString()));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.getSucceeded()).isEqualTo(1000);
        assertThat(countSkus("csvtest-bulk-")).isEqualTo(1000);

        long queries = stats.getPrepareStatementCount();
        System.out.printf("%n[1000행 결과] 소요 %dms, 총 statement %d회 (행당 %.2f회)%n",
                elapsed, queries, queries / 1000.0);

        // 검증 2회 + 행당 INSERT 3종(sku, review_stats, ledger) 수준이어야 한다.
        // 행마다 조회가 늘어나면 이 상한을 넘는다.
        assertThat(queries).as("행당 4회를 넘으면 어딘가에서 행 단위 조회가 늘어난 것")
                .isLessThan(1000 * 4);
    }

    // ── Helpers ───────────────────────────────────────────

    /** 자주 바뀌지 않는 값은 고정하고 slug·이름·가격·재고만 바꾼다 */
    private String row(String slug, String name, String listPrice, String initialStock) {
        return String.join(",",
                artistCode, name, slug, listPrice,
                "ARTWORK", "ART_TOY", "", initialStock, "false", "", "") + "\n";
    }

    private MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "skus.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
    }

    private int countSkus(String slugPrefix) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM skus WHERE slug LIKE ?", Integer.class, slugPrefix + "%");
        return c != null ? c : 0;
    }

    private int countReviewStats() {
        Integer c = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sku_review_stats
                WHERE sku_id IN (SELECT id FROM skus WHERE slug LIKE 'csvtest-%')
                """, Integer.class);
        return c != null ? c : 0;
    }

    private int stockOf(Long skuId) {
        Integer sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(delta), 0) FROM sku_stock_ledger WHERE sku_id = ?",
                Integer.class, skuId);
        return sum != null ? sum : 0;
    }
}
