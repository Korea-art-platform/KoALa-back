package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.sku.entity.SkuGenre;

import java.nio.charset.StandardCharsets;

/**
 * 일괄 등록용 빈 템플릿 csv 생성.
 *
 * <p>파서는 헤더를 <b>이름</b>으로 찾으므로 컬럼 순서는 자유지만,
 * 관리자가 채우기 쉽도록 필수값을 앞쪽에 모아둔다.
 */
public final class SkuCsvTemplate {

    private SkuCsvTemplate() {}

    /**
     * 엑셀은 csv 인코딩을 자동으로 알아내지 못한다.
     * 이 3바이트가 없으면 한글이 {@code ë¬´ì§€} 처럼 깨진 채로 열린다.
     */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String HEADER = String.join(",",
            // 필수
            "artistCode", "name", "slug", "listPrice",
            // 선택 — 분류·가격
            "skuType", "genre", "salePrice", "initialStock",
            // 선택 — 한정판 (셋은 세트로 채우거나 셋 다 비운다)
            "isLimitedEdition", "editionSize", "editionNumber",
            // 선택 — 설명
            "description", "material", "materialDescription",
            "packagingTitle", "packagingDescription",
            // 선택 — 이미지·규격
            "primaryImageUrl", "widthCm", "heightCm", "depthCm", "weightKg");

    /** 관리자가 형식을 보고 따라 쓸 수 있게 한 줄만 채워 둔다 */
    private static final String SAMPLE_ROW = String.join(",",
            "여기에_작가코드", "푸른 곰", "blue-bear-01", "150000",
            "ARTWORK", SkuGenre.DEFAULT.name(), "", "10",
            "false", "", "",
            "작가의 대표 시리즈 첫 번째 작품", "레진", "무광 마감 레진",
            "기본 패키지", "전용 박스 + 인증서",
            "", "12.5", "20", "12.5", "0.85");

    public static byte[] build() {
        String csv = HEADER + "\r\n" + SAMPLE_ROW + "\r\n";
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, result, UTF8_BOM.length, body.length);
        return result;
    }
}
