package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// csv 파싱만 담당. db 는 모른다
@Component
public class SkuCsvParser {

    // 없으면 헤더 검증 실패
    private static final List<String> REQUIRED_HEADERS =
            List.of("artistCode", "name", "slug", "listPrice", "mainCategory", "genre");

    // 엑셀이 utf-8 저장 시 파일 맨 앞에 붙이는 문자
    private static final String BOM = "\uFEFF";

    @Value("${koala.csv.max-rows:5000}")
    private int maxRows;

    public List<SkuCsvDto.Row> parse(InputStream in) {
        try (CSVReader reader = new CSVReader(
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))) {

            Map<String, Integer> headerIndex = readHeader(reader);

            List<SkuCsvDto.Row> rows = new ArrayList<>();
            String[] cols;
            int rowNumber = 1;   // 헤더가 1행

            while ((cols = reader.readNext()) != null) {
                rowNumber++;
                if (isBlankRow(cols)) continue;

                if (rows.size() >= maxRows) {
                    throw new BusinessException(ErrorCode.CSV_TOO_MANY_ROWS,
                            "최대 " + maxRows + "행까지 등록할 수 있습니다. 파일을 나눠서 올려주세요.");
                }
                rows.add(toRow(cols, headerIndex, rowNumber));
            }

            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.CSV_EMPTY_FILE, "등록할 행이 없습니다.");
            }
            return rows;

        } catch (IOException | CsvValidationException e) {
            throw new BusinessException(ErrorCode.CSV_PARSE_FAILED);
        }
    }

    // ── 헤더 ─────────────────────────────────────────────

    private Map<String, Integer> readHeader(CSVReader reader)
            throws IOException, CsvValidationException {

        String[] header = reader.readNext();
        if (header == null) {
            throw new BusinessException(ErrorCode.CSV_EMPTY_FILE);
        }

        // 컬럼 순서는 자유. 이름으로 위치를 찾는다
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            index.put(normalize(header[i]), i);
        }

        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(h -> !index.containsKey(normalize(h)))
                .toList();

        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.CSV_INVALID_HEADER,
                    "필수 컬럼 누락: " + String.join(", ", missing)
                            + " (템플릿을 내려받아 UTF-8 로 저장했는지 확인해주세요)");
        }
        return index;
    }

    // bom 제거 + 공백 제거 + 대소문자 무시
    private String normalize(String header) {
        if (header == null) return "";
        return header.replace(BOM, "").trim().toLowerCase();
    }

    // ── 행 ───────────────────────────────────────────────

    private SkuCsvDto.Row toRow(String[] cols, Map<String, Integer> index, int rowNumber) {
        SkuCsvDto.Row row = new SkuCsvDto.Row();
        row.setRowNumber(rowNumber);

        row.setArtistCode(get(cols, index, "artistCode"));
        row.setName(get(cols, index, "name"));
        row.setSlug(get(cols, index, "slug"));
        row.setDescription(get(cols, index, "description"));
        row.setMainCategory(get(cols, index, "mainCategory"));
        row.setGenre(get(cols, index, "genre"));
        row.setMaterial(get(cols, index, "material"));
        row.setMaterialDescription(get(cols, index, "materialDescription"));
        row.setPackagingTitle(get(cols, index, "packagingTitle"));
        row.setPackagingDescription(get(cols, index, "packagingDescription"));
        row.setListPrice(get(cols, index, "listPrice"));
        row.setSalePrice(get(cols, index, "salePrice"));
        row.setEditionSize(get(cols, index, "editionSize"));
        row.setEditionNumber(get(cols, index, "editionNumber"));
        row.setPrimaryImageUrl(get(cols, index, "primaryImageUrl"));
        row.setWidthCm(get(cols, index, "widthCm"));
        row.setHeightCm(get(cols, index, "heightCm"));
        row.setDepthCm(get(cols, index, "depthCm"));
        row.setWeightKg(get(cols, index, "weightKg"));
        row.setInitialStock(get(cols, index, "initialStock"));

        return row;
    }

    // 없는 컬럼 / 짧은 행 / 빈 칸은 전부 null
    private String get(String[] cols, Map<String, Integer> index, String header) {
        Integer i = index.get(normalize(header));
        if (i == null || i >= cols.length || cols[i] == null) return null;

        String value = cols[i].trim();
        return value.isEmpty() ? null : value;
    }

    // 엑셀이 만드는 빈 줄
    private boolean isBlankRow(String[] cols) {
        return Arrays.stream(cols).allMatch(c -> c == null || c.isBlank());
    }
}
