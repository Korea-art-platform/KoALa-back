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

/**
 * csv 일괄 등록 오케스트레이션. 파싱 → 사전검증 → 청크 저장 순서만 담당한다.
 *
 * <p><b>이 클래스에는 {@code @Transactional} 을 붙이지 않는다.</b>
 * 여기에 트랜잭션이 열려 있으면 {@link SkuBulkWriter#writeChunk} 의 {@code REQUIRED} 가
 * 새 트랜잭션을 만들지 않고 합류해버려서, 5,000건이 하나의 거대한 트랜잭션이 된다.
 * 그러면 청크 분리도, "몇 건까지 저장됐는지" 보고도 성립하지 않는다.
 */
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

        // ① 파싱 — 파일 자체가 못 쓰는 경우는 여기서 예외
        List<SkuCsvDto.Row> rows = parse(file);
        log.info("csv 일괄 등록 시작: {}행", rows.size());

        // ② 사전검증 — db 는 읽기만 한다
        SkuCsvDto.ValidationResult validation = validator.validate(rows);

        // ③ 하나라도 틀리면 저장하지 않는다
        if (validation.hasErrors()) {
            log.info("csv 검증 실패로 저장 생략: 오류 {}건", validation.getErrors().size());
            return SkuCsvDto.ImportResult.of(rows.size(), 0, validation.getErrors());
        }

        // ④ 청크 단위 저장
        return writeAll(rows.size(), validation.getValidRows());
    }

    // ── 저장 ─────────────────────────────────────────────

    private SkuCsvDto.ImportResult writeAll(int totalRows, List<SkuCsvDto.ParsedRow> validRows) {
        List<SkuCsvDto.RowError> errors = new ArrayList<>();
        int saved = 0;

        for (int start = 0; start < validRows.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, validRows.size());
            List<SkuCsvDto.ParsedRow> chunk = validRows.subList(start, end);

            try {
                saved += writer.writeChunk(chunk);
            } catch (Exception e) {
                // 원인을 모르는 채로 계속 진행하면 같은 실패를 반복할 뿐이다. 즉시 멈춘다
                errors.add(stoppedError(chunk, validRows, e));
                log.error("csv 저장 중단: {}건 저장 후 실패", saved, e);
                break;
            }
        }

        log.info("csv 일괄 등록 종료: 총 {}행 중 {}건 저장", totalRows, saved);
        return SkuCsvDto.ImportResult.of(totalRows, saved, errors);
    }

    /**
     * 어디부터 저장되지 않았는지 정확히 남긴다.
     *
     * <p>재고가 누적 원장이라 이 숫자를 모르면 재업로드 시 재고가 두 배가 된다.
     * 이미 저장된 행은 slug 중복으로 걸리므로 파일에서 잘라내고 올려야 한다.
     */
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

    // ── 파일 읽기 ────────────────────────────────────────

    private List<SkuCsvDto.Row> parse(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            log.error("csv 파일 읽기 실패: filename={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.CSV_PARSE_FAILED);
        }
    }
}
