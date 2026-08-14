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

@Slf4j
@Component
@RequiredArgsConstructor
public class SkuBulkWriter {
    private final SkuRepository skuRepository;
    private final SkuReviewStatsRepository skuReviewStatsRepository;
    private final StockService stockService;
    private final CodeGenerator codeGenerator;
    private final EntityManager entityManager;

    @Transactional
    public int writeChunk(List<SkuCsvDto.ParsedRow> chunk) {
        for (SkuCsvDto.ParsedRow parsed : chunk) {
            save(parsed);
        }
        log.debug("csv 청크 저장 완료: {}건", chunk.size());
        return chunk.size();
    }

    private void save(SkuCsvDto.ParsedRow parsed) {
        SkuDto.CreateRequest req = parsed.getRequest();

        Artist artist = entityManager.getReference(Artist.class, parsed.getArtistId());

        Sku sku = Sku.builder()
                .skuCode(codeGenerator.generateCode())
                .artist(artist)
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .mainCategory(req.getMainCategory())
                .genre(req.getGenre())

                .material(req.getMaterial())
                .materialDescription(req.getMaterialDescription())
                .packagingTitle(req.getPackagingTitle())
                .packagingDescription(req.getPackagingDescription())
                .listPrice(req.getListPrice())
                .salePrice(req.getSalePrice())
                .editionSize(req.getEditionSize())
                .editionNumber(req.getEditionNumber())
                .primaryImageUrl(req.getPrimaryImageUrl())
                .widthCm(req.getWidthCm())
                .heightCm(req.getHeightCm())
                .depthCm(req.getDepthCm())
                .weightKg(req.getWeightKg())
                .build();

        skuRepository.save(sku);

        skuReviewStatsRepository.save(SkuReviewStats.builder().sku(sku).build());

        if (parsed.getInitialStock() > 0) {
            stockService.initialize(sku, parsed.getInitialStock(), "csv 일괄 등록");
        }
    }
}
