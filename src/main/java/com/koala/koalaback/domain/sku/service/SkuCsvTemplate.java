package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.domain.sku.entity.Sku;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 일괄 등록용 빈 템플릿 csv 생성.
 *
 * <p>파서는 헤더를 <b>이름</b>으로 찾으므로 컬럼 순서는 자유지만,
 * 관리자가 채우기 쉽도록 필수값을 앞쪽에 모아둔다.
 *
 * <p>카테고리 코드가 DB 로 옮겨가면서 정적 유틸에서 빈으로 바뀌었다.
 * 샘플 행에 <b>실제로 등록된 코드</b>가 들어가야 그대로 복사해 쓸 수 있다.
 */
@Component
@RequiredArgsConstructor
public class SkuCsvTemplate {

    private final SkuCategoryService categoryService;

    /**
     * 엑셀은 csv 인코딩을 자동으로 알아내지 못한다.
     * 이 3바이트가 없으면 한글이 {@code ë¬´ì§€} 처럼 깨진 채로 열린다.
     */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String HEADER = String.join(",",
            // 필수
            "artistCode", "name", "slug", "listPrice", "mainCategory", "genre",
            // 선택 — 가격·재고
            "salePrice", "initialStock",
            // 선택 — 에디션 (mainCategory 가 LIMITED 일 때만)
            "editionSize", "editionNumber",
            // 선택 — 설명
            "description", "material", "materialDescription",
            "packagingTitle", "packagingDescription",
            // 선택 — 이미지·규격
            "primaryImageUrl", "widthCm", "heightCm", "depthCm", "weightKg");

    public byte[] build() {
        SkuCategoryDto.GroupedResponse categories = categoryService.getActiveCategories();

        String csv = HEADER + "\r\n"
                + sampleRow(categories) + "\r\n"
                + guideRow(categories) + "\r\n";
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, result, UTF8_BOM.length, body.length);
        return result;
    }

    /** 관리자가 형식을 보고 따라 쓸 수 있게 한 줄만 채워 둔다 */
    private String sampleRow(SkuCategoryDto.GroupedResponse categories) {
        return String.join(",",
                "여기에_작가코드", "푸른 곰", "blue-bear-01", "150000",
                Sku.MAIN_NORMAL, firstCode(categories.getSub()),
                "", "10",
                "", "",
                "작가의 대표 시리즈 첫 번째 작품", "레진", "무광 마감 레진",
                "기본 패키지", "전용 박스 + 인증서",
                "", "12.5", "20", "12.5", "0.85");
    }

    /**
     * 두 번째 줄은 <b>사용 가능한 카테고리 코드 목록</b>이다.
     *
     * <p>업로드 전에 지우라고 안내한다. 코드를 외우거나 화면을 오가며 확인하지 않아도 된다.
     * csv 에 주석 문법이 없어 이렇게 넣는다 — artistCode 가 없으니 실수로 남겨도 등록되지 않는다.
     */
    private String guideRow(SkuCategoryDto.GroupedResponse categories) {
        return String.join(",",
                "↑ 이 줄은 지우고 사용하세요", "", "", "",
                quote(codeGuide(categories.getMain())),
                quote(codeGuide(categories.getSub())),
                "", "", "", "", "", "", "", "", "", "", "", "", "", "");
    }

    private String codeGuide(List<SkuCategoryDto.Response> list) {
        return list.stream()
                .map(c -> c.getCode() + "=" + c.getName())
                .reduce((a, b) -> a + " / " + b)
                .orElse("");
    }

    private String firstCode(List<SkuCategoryDto.Response> list) {
        return list.isEmpty() ? "" : list.get(0).getCode();
    }

    /** 값에 쉼표가 들어가므로 큰따옴표로 감싼다 */
    private String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
