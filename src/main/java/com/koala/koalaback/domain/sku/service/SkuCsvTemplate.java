package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.category.dto.SkuCategoryDto;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
import com.koala.koalaback.domain.sku.entity.Sku;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SkuCsvTemplate {
    private final SkuCategoryService categoryService;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String HEADER = String.join(",",

            "artistCode", "name", "slug", "listPrice", "mainCategory", "genre",

            "salePrice", "initialStock",

            "editionSize", "editionNumber",

            "description", "material", "materialDescription",
            "packagingTitle", "packagingDescription",

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

    private String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
