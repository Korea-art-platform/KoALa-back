package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.category.entity.SkuCategory;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * csv 사전검증 규칙 단위 테스트.
 *
 * <p>통합 테스트가 큰 흐름을 본다면 여기서는 규칙 하나하나를 본다.
 * 검증기가 놓친 제약은 저장 도중 SQL 예외가 되고 앞 청크는 이미 커밋된 뒤라
 * 부분 등록이 되므로, DB 제약을 그대로 흉내내는지 확인하는 것이 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("csv 사전검증 규칙")
class SkuCsvValidatorTest {

    private static final String ARTIST_CODE = "ART-001";

    @InjectMocks private SkuCsvValidator validator;

    @Mock private SkuRepository skuRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private SkuCategoryService categoryService;

    @BeforeEach
    void setUp() {
        Artist artist = mock(Artist.class);
        given(artist.getArtistCode()).willReturn(ARTIST_CODE);
        given(artist.getId()).willReturn(1L);

        given(artistRepository.findAllByArtistCodeIn(any())).willReturn(List.of(artist));
        given(skuRepository.findExistingSlugs(any())).willReturn(List.of());

        // 카테고리 허용값은 이제 상수가 아니라 db 에서 온다
        given(categoryService.getActiveCodesByType()).willReturn(Map.of(
                SkuCategory.TYPE_MAIN, Set.of("LIMITED", "NORMAL"),
                SkuCategory.TYPE_SUB, Set.of("SCULPTURE", "ART_TOY")));
    }

    @Nested
    @DisplayName("필수값")
    class Required {

        @Test
        @DisplayName("artistCode·name·slug·listPrice 가 비면 각각 오류가 쌓인다")
        void missingRequiredFields() {
            SkuCsvDto.Row row = row();
            row.setArtistCode(null);
            row.setName(null);
            row.setSlug(null);
            row.setListPrice(null);

            SkuCsvDto.ValidationResult result = validate(row);

            assertThat(result.getValidRows()).isEmpty();
            assertThat(result.getErrors())
                    .extracting(SkuCsvDto.RowError::getField)
                    .containsExactlyInAnyOrder("artistCode", "name", "slug", "listPrice");
        }

        @Test
        @DisplayName("한 행에서 여러 오류를 한꺼번에 모은다 — 왕복을 줄이기 위해")
        void collectsAllErrorsInOneRow() {
            SkuCsvDto.Row row = row();
            row.setName(null);
            row.setListPrice("abc");
            row.setInitialStock("-5");

            SkuCsvDto.ValidationResult result = validate(row);

            assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("slug")
    class Slug {

        @Test
        @DisplayName("영문 소문자·숫자·하이픈만 허용한다")
        void validSlugPasses() {
            assertThat(validate(rowWithSlug("blue-bear-01")).getValidRows()).hasSize(1);
        }

        @Test
        @DisplayName("대문자·한글·공백·언더바는 거절한다 — url 이 깨진다")
        void invalidSlugRejected() {
            for (String bad : List.of("Blue-Bear", "푸른곰", "blue bear", "blue_bear")) {
                assertThat(validate(rowWithSlug(bad)).getErrors())
                        .as("slug=%s", bad)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("하이픈이 양끝에 오거나 연속되면 거절한다")
        void hyphenEdgeCasesRejected() {
            for (String bad : List.of("-bear", "bear-", "blue--bear")) {
                assertThat(validate(rowWithSlug(bad)).getErrors())
                        .as("slug=%s", bad)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("파일 안에서 겹치면 양쪽 행을 모두 뺀다 — 어느 쪽이 맞는지 알 수 없다")
        void duplicateInFileRejectsBoth() {
            SkuCsvDto.Row first = rowWithSlug("same-slug");
            first.setRowNumber(2);
            SkuCsvDto.Row second = rowWithSlug("same-slug");
            second.setRowNumber(3);

            SkuCsvDto.ValidationResult result = validator.validate(List.of(first, second));

            assertThat(result.getValidRows()).isEmpty();
            assertThat(result.getErrors())
                    .hasSize(2)
                    .extracting(SkuCsvDto.RowError::getRowNumber)
                    .containsExactlyInAnyOrder(2, 3);
        }

        @Test
        @DisplayName("이미 등록된 slug 는 거절한다 — soft delete 된 것도 UNIQUE 를 점유한다")
        void existingSlugRejected() {
            given(skuRepository.findExistingSlugs(any())).willReturn(List.of("taken-slug"));

            SkuCsvDto.ValidationResult result = validate(rowWithSlug("taken-slug"));

            assertThat(result.getValidRows()).isEmpty();
            assertThat(result.getErrors().get(0).getField()).isEqualTo("slug");
        }
    }

    @Nested
    @DisplayName("가격")
    class Price {

        @Test
        @DisplayName("판매가가 정가보다 크면 거절한다")
        void salePriceOverListPrice() {
            SkuCsvDto.Row row = row();
            row.setListPrice("10000");
            row.setSalePrice("20000");

            assertThat(validate(row).getErrors())
                    .extracting(SkuCsvDto.RowError::getField)
                    .contains("salePrice");
        }

        @Test
        @DisplayName("판매가가 정가와 같으면 통과한다")
        void salePriceEqualToListPrice() {
            SkuCsvDto.Row row = row();
            row.setListPrice("10000");
            row.setSalePrice("10000");

            assertThat(validate(row).getValidRows()).hasSize(1);
        }

        @Test
        @DisplayName("소수점 3자리는 거절한다 — 반올림하면 금액이 조용히 바뀐다")
        void tooManyDecimalPlaces() {
            SkuCsvDto.Row row = row();
            row.setListPrice("10000.005");

            assertThat(validate(row).getErrors())
                    .extracting(SkuCsvDto.RowError::getField)
                    .contains("listPrice");
        }

        @Test
        @DisplayName("정수부가 12자리를 넘으면 거절한다 — decimal(13,2) 를 넘긴다")
        void tooLargeInteger() {
            SkuCsvDto.Row row = row();
            row.setListPrice("123456789012");

            assertThat(validate(row).getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("음수는 거절한다")
        void negativePrice() {
            SkuCsvDto.Row row = row();
            row.setListPrice("-1000");

            assertThat(validate(row).getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("천 단위 구분자가 있어도 읽는다 — 엑셀에서 흔하다")
        void thousandSeparatorAccepted() {
            SkuCsvDto.Row row = row();
            row.setListPrice("1,500,000");

            SkuCsvDto.ValidationResult result = validate(row);

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getValidRows().get(0).getRequest().getListPrice())
                    .isEqualByComparingTo(BigDecimal.valueOf(1_500_000));
        }
    }

    @Nested
    @DisplayName("카테고리")
    class Categories {

        @Test
        @DisplayName("대분류·소분류는 필수다 — 비면 상품이 어디에도 안 걸린다")
        void categoriesRequired() {
            SkuCsvDto.Row row = row();
            row.setMainCategory(null);
            row.setGenre(null);

            assertThat(validate(row).getErrors())
                    .extracting(SkuCsvDto.RowError::getField)
                    .contains("mainCategory", "genre");
        }

        @Test
        @DisplayName("등록되지 않은 코드는 거절한다 — 오타를 막지 않으면 필터에서 영영 안 잡힌다")
        void unknownCodeRejected() {
            SkuCsvDto.Row bad = row();
            bad.setGenre("ART_TOYY");
            assertThat(validate(bad).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("genre");

            SkuCsvDto.Row badMain = row();
            badMain.setMainCategory("LIMITE");
            assertThat(validate(badMain).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("mainCategory");
        }

        @Test
        @DisplayName("허용값은 db 에서 온다 — 관리자가 추가한 코드도 배포 없이 통과한다")
        void codesComeFromDb() {
            given(categoryService.getActiveCodesByType()).willReturn(Map.of(
                    SkuCategory.TYPE_MAIN, Set.of("NORMAL"),
                    SkuCategory.TYPE_SUB, Set.of("CERAMIC")));   // 운영에서 새로 추가된 소분류

            SkuCsvDto.Row row = row();
            row.setGenre("CERAMIC");

            assertThat(validate(row).getValidRows()).hasSize(1);
        }

        @Test
        @DisplayName("소문자로 적어도 대문자로 정규화한다")
        void lowercaseNormalized() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("normal");
            row.setGenre("sculpture");

            SkuCsvDto.ParsedRow parsed = validate(row).getValidRows().get(0);

            assertThat(parsed.getRequest().getMainCategory()).isEqualTo("NORMAL");
            assertThat(parsed.getRequest().getGenre()).isEqualTo("SCULPTURE");
        }
    }

    @Nested
    @DisplayName("한정판 에디션")
    class Edition {

        @Test
        @DisplayName("한정판이면 size 만 채워도 된다 — 번호는 나중에 정할 수 있다")
        void numberIsOptional() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("LIMITED");
            row.setEditionSize("100");
            row.setEditionNumber(null);

            assertThat(validate(row).getValidRows()).hasSize(1);
        }

        @Test
        @DisplayName("번호만 있고 총 수량이 없으면 거절한다 — CHECK 제약 위반이 된다")
        void numberWithoutSizeRejected() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("LIMITED");
            row.setEditionNumber("7");

            assertThat(validate(row).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("editionSize");
        }

        @Test
        @DisplayName("한정판이 아닌데 에디션 값이 있으면 거절한다")
        void nonLimitedMustBeEmpty() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("NORMAL");
            row.setEditionSize("100");

            assertThat(validate(row).getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("에디션 번호가 총 수량보다 크면 거절한다")
        void numberOverSize() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("LIMITED");
            row.setEditionSize("10");
            row.setEditionNumber("11");

            assertThat(validate(row).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("editionNumber");
        }

        @Test
        @DisplayName("0 이하는 거절한다")
        void zeroOrNegativeRejected() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("LIMITED");
            row.setEditionSize("0");
            row.setEditionNumber("0");

            assertThat(validate(row).getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("정상 한정판은 통과한다")
        void validLimitedEdition() {
            SkuCsvDto.Row row = row();
            row.setMainCategory("LIMITED");
            row.setEditionSize("100");
            row.setEditionNumber("7");

            assertThat(validate(row).getValidRows()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("재고·길이·작가")
    class Etc {

        @Test
        @DisplayName("재고가 음수면 거절하고, 비면 0 이 된다")
        void stockRules() {
            SkuCsvDto.Row negative = row();
            negative.setInitialStock("-1");
            assertThat(validate(negative).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("initialStock");

            SkuCsvDto.Row blank = row();
            blank.setInitialStock(null);
            assertThat(validate(blank).getValidRows().get(0).getInitialStock()).isZero();
        }

        @Test
        @DisplayName("컬럼 길이를 넘으면 거절한다 — 저장 시점에 터지면 부분 등록이 된다")
        void lengthLimits() {
            SkuCsvDto.Row longName = row();
            longName.setName("가".repeat(201));
            assertThat(validate(longName).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("name");

            SkuCsvDto.Row longUrl = row();
            longUrl.setPrimaryImageUrl("https://x.test/" + "a".repeat(700));
            assertThat(validate(longUrl).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("primaryImageUrl");
        }

        @Test
        @DisplayName("존재하지 않는 작가 코드는 거절한다")
        void unknownArtist() {
            SkuCsvDto.Row row = row();
            row.setArtistCode("NO-SUCH");

            assertThat(validate(row).getErrors())
                    .extracting(SkuCsvDto.RowError::getField).contains("artistCode");
        }

        @Test
        @DisplayName("통과한 행에는 작가 id 가 채워진다")
        void artistIdResolved() {
            assertThat(validate(row()).getValidRows().get(0).getArtistId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("createSku 가 빠뜨리는 4개 필드도 그대로 실어 보낸다")
        void carriesMaterialAndPackaging() {
            SkuCsvDto.Row row = row();
            row.setMaterial("레진");
            row.setMaterialDescription("무광 마감");
            row.setPackagingTitle("기본 패키지");
            row.setPackagingDescription("전용 박스");

            var req = validate(row).getValidRows().get(0).getRequest();

            assertThat(req.getMaterial()).isEqualTo("레진");
            assertThat(req.getMaterialDescription()).isEqualTo("무광 마감");
            assertThat(req.getPackagingTitle()).isEqualTo("기본 패키지");
            assertThat(req.getPackagingDescription()).isEqualTo("전용 박스");
        }
    }

    // ── Helpers ───────────────────────────────────────────

    private SkuCsvDto.ValidationResult validate(SkuCsvDto.Row row) {
        return validator.validate(List.of(row));
    }

    private SkuCsvDto.Row rowWithSlug(String slug) {
        SkuCsvDto.Row row = row();
        row.setSlug(slug);
        return row;
    }

    /** 통과하는 최소 행 — 각 테스트에서 검사할 필드만 바꾼다 */
    private SkuCsvDto.Row row() {
        SkuCsvDto.Row row = new SkuCsvDto.Row();
        row.setRowNumber(2);
        row.setArtistCode(ARTIST_CODE);
        row.setName("정상 상품");
        row.setSlug("normal-sku-01");
        row.setListPrice("150000");
        row.setMainCategory("NORMAL");
        row.setGenre("SCULPTURE");
        row.setInitialStock("10");
        return row;
    }
}
