package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.domain.sku.dto.SkuDto;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.entity.SkuMedia;
import com.koala.koalaback.domain.sku.entity.SkuReviewStats;
import com.koala.koalaback.domain.sku.repository.SkuMediaRepository;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.domain.sku.repository.SkuReviewStatsRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.infra.storage.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkuService {

    private final SkuRepository skuRepository;
    private final SkuMediaRepository skuMediaRepository;
    private final SkuReviewStatsRepository skuReviewStatsRepository;
    private final ArtistService artistService;
    private final StockService stockService;
    private final CodeGenerator codeGenerator;
    private final StorageUploader s3Uploader;

    // ── 공개 조회 ─────────────────────────────────────────

    public PageResponse<SkuDto.SummaryResponse> getActiveSkus(Pageable pageable) {
        return toSummaryPage(skuRepository
                .findByStatusAndDeletedAtIsNull("ACTIVE", pageable));
    }

    public PageResponse<SkuDto.SummaryResponse> getSkusByArtist(String artistCode, Pageable pageable) {
        Artist artist = artistService.getArtistEntityByCode(artistCode);
        return toSummaryPage(skuRepository
                .findByArtistIdAndStatusAndDeletedAtIsNull(artist.getId(), "ACTIVE", pageable));
    }

    public SkuDto.DetailResponse getSkuBySlug(String slug) {
        Sku sku = skuRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
        return toDetail(sku);
    }

    public SkuDto.DetailResponse getSkuByCode(String skuCode) {
        return toDetail(getSkuEntityByCode(skuCode));
    }

    public Map<String, Long> getGenreCounts() {
        long total = skuRepository.countByStatusAndDeletedAtIsNull("ACTIVE");
        Map<String, Long> result = new HashMap<>();
        result.put("ALL", total);
        skuRepository.countByGenre().forEach(row ->
                result.put((String) row[0], (Long) row[1]));
        return result;
    }

    // ── 360도 프레임 조회 (Redis 캐시) ────────────────────

    @Cacheable(value = "sku360frames", key = "#skuCode")
    public SkuDto.FrameListResponse get360Frames(String skuCode) {
        Sku sku = getSkuEntityByCode(skuCode);
        List<SkuMedia> frames = skuMediaRepository
                .findBySkuIdAndMediaRoleOrderByAngleDegreeAsc(sku.getId(), "SPINE_360");
        return SkuDto.FrameListResponse.builder()
                .skuCode(skuCode)
                .frameCount(frames.size())
                .frames(frames.stream().map(SkuDto.MediaResponse::from).toList())
                .build();
    }

    // ── 어드민 CRUD ───────────────────────────────────────

    @Transactional
    public SkuDto.SummaryResponse createSku(SkuDto.CreateRequest req) {
        if (skuRepository.existsBySlug(req.getSlug())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Artist artist = artistService.getArtistEntityByCode(req.getArtistCode());
        Sku sku = Sku.builder()
                .skuCode(codeGenerator.generateCode())
                .artist(artist)
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .skuType(req.getSkuType())
                .genre(req.getGenre())
                .listPrice(req.getListPrice())
                .salePrice(req.getSalePrice())
                .isLimitedEdition(req.getIsLimitedEdition())
                .editionSize(req.getEditionSize())
                .editionNumber(req.getEditionNumber())
                .badges(req.getBadges())
                .primaryImageUrl(req.getPrimaryImageUrl())
                .widthCm(req.getWidthCm())
                .heightCm(req.getHeightCm())
                .depthCm(req.getDepthCm())
                .weightKg(req.getWeightKg())
                .build();
        skuRepository.save(sku);
        skuReviewStatsRepository.save(SkuReviewStats.builder().sku(sku).build());
        return toSummary(sku);
    }

    @Transactional
    @CacheEvict(value = "sku360frames", key = "#skuCode")
    public SkuDto.SummaryResponse updateSku(String skuCode, SkuDto.UpdateRequest req) {
        Sku sku = getSkuEntityByCode(skuCode);
        if (!sku.getSlug().equals(req.getSlug()) && skuRepository.existsBySlug(req.getSlug())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        sku.update(req.getName(), req.getSlug(), req.getDescription(),
                req.getSkuType(), req.getGenre(), req.getMaterial(),
                req.getMaterialDescription(), req.getPackagingTitle(), req.getPackagingDescription(),
                req.getListPrice(), req.getSalePrice(), req.getPrimaryImageUrl(),
                req.getIsLimitedEdition(), req.getEditionSize(), req.getEditionNumber(),
                req.getBadges());
        return toSummary(sku);
    }

    @Transactional
    public void publishSku(String skuCode) {
        getSkuEntityByCode(skuCode).publish();
    }

    @Transactional
    public void discontinueSku(String skuCode) {
        getSkuEntityByCode(skuCode).discontinue();
    }

    @Transactional
    public void deleteSku(String skuCode) {
        getSkuEntityByCode(skuCode).softDelete();
    }

    // ── 360도 프레임 업로드 (어드민) ──────────────────────

    @Transactional
    @CacheEvict(value = "sku360frames", key = "#skuCode")
    public SkuDto.FrameListResponse upload360Frames(String skuCode, List<SkuDto.FrameUploadItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 각도 범위 및 중복 검증
        items.forEach(i -> {
            if (i.getAngleDegree() == null
                    || i.getAngleDegree().doubleValue() < 0
                    || i.getAngleDegree().doubleValue() >= 360) {
                throw new BusinessException(ErrorCode.INVALID_ANGLE_DEGREE);
            }
        });

        long distinctCount = items.stream()
                .map(i -> i.getAngleDegree().stripTrailingZeros().toPlainString())
                .distinct()
                .count();
        if (distinctCount != items.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ANGLE_DEGREE);
        }

        Sku sku = getSkuEntityByCode(skuCode);
        skuMediaRepository.deleteBySkuIdAndMediaRole(sku.getId(), "SPINE_360");

        List<SkuDto.FrameUploadItem> sorted = items.stream()
                .sorted(Comparator.comparing(SkuDto.FrameUploadItem::getAngleDegree))
                .toList();

        List<SkuMedia> mediaList = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            SkuDto.FrameUploadItem item = sorted.get(i);
            mediaList.add(SkuMedia.builder()
                    .sku(sku)
                    .mediaType("IMAGE")
                    .mediaRole("SPINE_360")
                    .fileUrl(item.getFileUrl())
                    .thumbnailUrl(item.getThumbnailUrl())
                    .angleDegree(item.getAngleDegree())
                    .sortOrder(i)
                    .isPrimary(false)
                    .build());
        }
        skuMediaRepository.saveAll(mediaList);

        return SkuDto.FrameListResponse.builder()
                .skuCode(skuCode)
                .frameCount(mediaList.size())
                .frames(mediaList.stream().map(SkuDto.MediaResponse::from).toList())
                .build();
    }

    // ── 미디어 관리 (어드민) ──────────────────────────────

    @Transactional
    public SkuDto.MediaResponse addMedia(String skuCode, MultipartFile file,
                                         SkuDto.MediaAddRequest req) {
        Sku sku = getSkuEntityByCode(skuCode);
        String dir = "skus/" + sku.getSkuCode() + "/" + req.getMediaRole().toLowerCase();
        String fileUrl = s3Uploader.upload(file, dir);

        List<SkuMedia> existing = skuMediaRepository
                .findBySkuIdAndMediaRoleOrderBySortOrderAsc(sku.getId(), req.getMediaRole());
        int nextOrder = req.getSortOrder() != null ? req.getSortOrder() : existing.size();

        boolean makePrimary = Boolean.TRUE.equals(req.getIsPrimary());

        SkuMedia media = SkuMedia.builder()
                .sku(sku)
                .mediaType(req.getMediaType())
                .mediaRole(req.getMediaRole())
                .fileUrl(fileUrl)
                .altText(req.getAltText())
                .sortOrder(nextOrder)
                .isPrimary(makePrimary)
                .build();
        skuMediaRepository.save(media);

        // 대표 이미지로 지정된 경우 SKU.primaryImageUrl 동기화
        if (makePrimary) {
            sku.update(sku.getName(), sku.getSlug(), sku.getDescription(),
                    sku.getSkuType(), sku.getGenre(), sku.getMaterial(),
                    sku.getMaterialDescription(), sku.getPackagingTitle(), sku.getPackagingDescription(),
                    sku.getListPrice(), sku.getSalePrice(), fileUrl,
                    sku.getIsLimitedEdition(), sku.getEditionSize(), sku.getEditionNumber(),
                    sku.getBadges());
        }

        return SkuDto.MediaResponse.from(media);
    }

    @Transactional
    public void deleteMedia(String skuCode, Long mediaId) {
        Sku sku = getSkuEntityByCode(skuCode);
        SkuMedia media = skuMediaRepository.findById(mediaId)
                .filter(m -> m.getSku().getId().equals(sku.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        s3Uploader.delete(media.getFileUrl());
        skuMediaRepository.delete(media);
    }

    public List<SkuDto.MediaResponse> getMediaList(String skuCode) {
        Sku sku = getSkuEntityByCode(skuCode);
        return skuMediaRepository
                .findBySkuIdOrderByMediaRoleAscSortOrderAsc(sku.getId())
                .stream().map(SkuDto.MediaResponse::from).toList();
    }

    // ── 재고 조회 ─────────────────────────────────────────

    public SkuDto.StockResponse getStock(String skuCode) {
        Sku sku = getSkuEntityByCode(skuCode);
        return SkuDto.StockResponse.builder()
                .skuCode(skuCode)
                .stockQuantity(stockService.getStock(sku.getId()))
                .build();
    }

    // ── Package-level helpers ─────────────────────────────

    public Sku getSkuEntityByCode(String skuCode) {
        return skuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    public Sku getSkuEntityById(Long id) {
        return skuRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    // ── Private helpers ───────────────────────────────────

    private SkuDto.SummaryResponse toSummary(Sku sku) {
        int stock = stockService.getStock(sku.getId());
        SkuReviewStats stats = skuReviewStatsRepository.findById(sku.getId()).orElse(null);
        return SkuDto.SummaryResponse.from(sku, stock, stats);
    }

    /**
     * 목록 응답 조립 — 재고/리뷰집계를 SKU 별로 조회하면 N+1 이 되므로
     * 페이지의 skuId 를 모아 한 번에 배치 조회한 뒤 매핑한다.
     * (응답 스키마는 {@link #toSummary} 와 동일)
     */
    private PageResponse<SkuDto.SummaryResponse> toSummaryPage(Page<Sku> skuPage) {
        List<Long> skuIds = skuPage.getContent().stream().map(Sku::getId).toList();

        Map<Long, Integer> stockMap = stockService.getStocks(skuIds);
        Map<Long, SkuReviewStats> statsMap = skuIds.isEmpty()
                ? Map.of()
                : skuReviewStatsRepository.findAllBySkuIdIn(skuIds).stream()
                        .collect(Collectors.toMap(SkuReviewStats::getSkuId, stats -> stats));

        return PageResponse.of(skuPage.map(sku -> SkuDto.SummaryResponse.from(
                sku,
                stockMap.getOrDefault(sku.getId(), 0),
                statsMap.get(sku.getId()))));
    }

    private SkuDto.DetailResponse toDetail(Sku sku) {
        int stock = stockService.getStock(sku.getId());
        SkuReviewStats stats = skuReviewStatsRepository.findById(sku.getId()).orElse(null);
        List<SkuMedia> media = skuMediaRepository
                .findBySkuIdOrderByMediaRoleAscSortOrderAsc(sku.getId());
        return SkuDto.DetailResponse.from(sku, stock, stats, media);
    }


    public PageResponse<SkuDto.SummaryResponse> getAllSkus(Pageable pageable) {
        return toSummaryPage(skuRepository.findByDeletedAtIsNull(pageable));
    }
}