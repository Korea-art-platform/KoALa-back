package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.sku.dto.SkuCsvDto;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuReviewStats;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.domain.sku.repository.SkuReviewStatsRepository;
import com.koala.koalaback.global.util.CodeGenerator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * csv 일괄 등록의 저장 단계. 청크 1개 = 트랜잭션 1개.
 *
 * <p><b>반드시 별도 빈이어야 한다.</b> 오케스트레이터 안에서 {@code this.writeChunk()} 로 부르면
 * Spring 프록시를 거치지 않아 {@code @Transactional} 이 조용히 무시된다(자기호출).
 * 에러도 나지 않고 그냥 트랜잭션 없이 돌아서, 청크 단위 원자성이 사라진다.
 *
 * <p>검증({@link SkuCsvValidator})을 통과한 행만 들어온다고 전제한다.
 * 여기서 예외가 나면 그 청크는 통째로 롤백되지만 <b>앞 청크는 이미 커밋된 뒤다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkuBulkWriter {

    private final SkuRepository skuRepository;
    private final SkuReviewStatsRepository skuReviewStatsRepository;
    private final StockService stockService;
    private final CodeGenerator codeGenerator;
    private final EntityManager entityManager;

    /**
     * 청크 하나를 저장한다.
     *
     * @return 저장된 행 수
     */
    @Transactional
    public int writeChunk(List<SkuCsvDto.ParsedRow> chunk) {
        for (SkuCsvDto.ParsedRow parsed : chunk) {
            save(parsed);
        }
        log.debug("csv 청크 저장 완료: {}건", chunk.size());
        return chunk.size();
    }

    // ── 행 1건 저장 ──────────────────────────────────────

    private void save(SkuCsvDto.ParsedRow parsed) {
        SkuDto.CreateRequest req = parsed.getRequest();

        // FK 값만 필요하므로 프록시로 충분하다 — 작가 SELECT 가 나가지 않는다
        Artist artist = entityManager.getReference(Artist.class, parsed.getArtistId());

        Sku sku = Sku.builder()
                .skuCode(codeGenerator.generateCode())
                .artist(artist)
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .skuType(req.getSkuType())
                .genre(req.getGenre())
                // createSku 가 빠뜨리고 있는 4개 — csv 컬럼에는 있으므로 여기서는 저장한다
                .material(req.getMaterial())
                .materialDescription(req.getMaterialDescription())
                .packagingTitle(req.getPackagingTitle())
                .packagingDescription(req.getPackagingDescription())
                .listPrice(req.getListPrice())
                .salePrice(req.getSalePrice())
                .isLimitedEdition(req.getIsLimitedEdition())
                .editionSize(req.getEditionSize())
                .editionNumber(req.getEditionNumber())
                .primaryImageUrl(req.getPrimaryImageUrl())
                .widthCm(req.getWidthCm())
                .heightCm(req.getHeightCm())
                .depthCm(req.getDepthCm())
                .weightKg(req.getWeightKg())
                .build();

        // IDENTITY 라 save() 시점에 INSERT 가 나가고 id 가 채워진다.
        // 아래 두 단계가 id 를 필요로 하므로 순서를 바꾸면 안 된다.
        skuRepository.save(sku);

        // 이 행이 없으면 리뷰 승인 시 recalculateBySkuId 의 UPDATE 가 0건이 되어
        // 집계가 조용히 반영되지 않는다 (createSku 도 동일하게 만든다)
        skuReviewStatsRepository.save(SkuReviewStats.builder().sku(sku).build());

        // 재고는 누적 원장이라 "설정"이 아니라 "추가"다. 0 이면 행을 남기지 않는다
        if (parsed.getInitialStock() > 0) {
            stockService.initialize(sku, parsed.getInitialStock(), "csv 일괄 등록");
        }
    }
}
