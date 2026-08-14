package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkuCsvImportService {
    private final SkuCsvParser parser;
    private final SkuCsvValidator validator;
    private final SkuBulkWriter writer;

    @Value("${koala.csv.chunk-size:1000}")
    private int chunkSize;

    public SkuCsvDto.ImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.CSV_EMPTY_FILE);
        }

        List<SkuCsvDto.Row> rows = parse(file);
        log.info("csv 일괄 등록 시작: {}행", rows.size());

        SkuCsvDto.ValidationResult validation = validator.validate(rows);

        if (validation.hasErrors()) {
            log.info("csv 검증 실패로 저장 생략: 오류 {}건", validation.getErrors().size());
            return SkuCsvDto.ImportResult.of(rows.size(), 0, validation.getErrors());
        }

        return writeAll(rows.size(), validation.getValidRows());
    }

    private SkuCsvDto.ImportResult writeAll(int totalRows, List<SkuCsvDto.ParsedRow> validRows) {
        List<SkuCsvDto.RowError> errors = new ArrayList<>();
        int saved = 0;

        for (int start = 0; start < validRows.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, validRows.size());
            List<SkuCsvDto.ParsedRow> chunk = validRows.subList(start, end);

            try {
                saved += writer.writeChunk(chunk);
            } catch (Exception e) {
                errors.add(stoppedError(chunk, validRows, e));
                log.error("csv 저장 중단: {}건 저장 후 실패", saved, e);
                break;
            }
        }

        log.info("csv 일괄 등록 종료: 총 {}행 중 {}건 저장", totalRows, saved);
        return SkuCsvDto.ImportResult.of(totalRows, saved, errors);
    }

    private SkuCsvDto.RowError stoppedError(List<SkuCsvDto.ParsedRow> failedChunk,
                                            List<SkuCsvDto.ParsedRow> validRows,
                                            Exception cause) {
        int fromRow = failedChunk.get(0).getRowNumber();
        int toRow = validRows.get(validRows.size() - 1).getRowNumber();

        return SkuCsvDto.RowError.of(fromRow, "-", null,
                "저장 중 오류가 발생해 중단했습니다. " + fromRow + "행부터 " + toRow + "행까지 저장되지 않았습니다. "
                        + "이미 저장된 앞부분은 파일에서 제외하고 다시 올려주세요. (원인: "
                        + cause.getClass().getSimpleName() + ")");
    }

    private List<SkuCsvDto.Row> parse(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            log.error("csv 파일 읽기 실패: filename={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.CSV_PARSE_FAILED);
        }
    }
}
