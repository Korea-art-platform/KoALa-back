package com.koala.koalaback.domain.sku.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.domain.category.service.SkuCategoryService;
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

import java.util.stream.Stream;
import java.math.BigDecimal;

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
    private final SkuCategoryService categoryService;
    private final StockService stockService;
    private final CodeGenerator codeGenerator;
    private final StorageUploader s3Uploader;

    public PageResponse<SkuDto.SummaryResponse> getActiveSkus(Pageable pageable) {
        return getActiveSkus(null, null, pageable);
    }

    /**
     * 소분류(genre)와 대분류(mainCategory)로 거른다. 둘 다 없으면 전체다.
     *
     * 거르는 일을 화면이 아니라 여기서 하는 이유는, 화면은 한 번에 한 페이지만
     * 받기 때문이다. 받아 온 12개 안에서 걸러 봐야 뒤 페이지에 있는 작품은
     * 세지 못해 "0점"으로 보인다. 페이지 수도 여기서 맞춰 돌려준다.
     */
    public PageResponse<SkuDto.SummaryResponse> getActiveSkus(String genre, String mainCategory,
                                                              Pageable pageable) {
        boolean byGenre = hasText(genre);
        boolean byMain = hasText(mainCategory);

        Page<Sku> page;
        if (byGenre && byMain) {
            page = skuRepository.findActiveByGenreAndMainCategory(genre, mainCategory, pageable);
        } else if (byGenre) {
            page = skuRepository.findActiveByGenre(genre, pageable);
        } else if (byMain) {
            page = skuRepository.findActiveByMainCategory(mainCategory, pageable);
        } else {
            page = skuRepository.findByStatusAndDeletedAtIsNull("ACTIVE", pageable);
        }
        return toSummaryPage(page);
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

    /** 스토어 대분류 칩이 작품 없는 분류를 숨기려면 개수를 알아야 한다. */
    public Map<String, Long> getMainCategoryCounts() {
        long total = skuRepository.countByStatusAndDeletedAtIsNull("ACTIVE");
        Map<String, Long> result = new HashMap<>();
        result.put("ALL", total);
        skuRepository.countByMainCategory().forEach(row ->
                result.put((String) row[0], (Long) row[1]));
        return result;
    }

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

    @Transactional
    public SkuDto.SummaryResponse createSku(SkuDto.CreateRequest req) {
        categoryService.validateCodes(req.getMainCategory(), req.getGenre());
        validateEdition(req.getMainCategory(), req.getEditionSize(), req.getEditionNumber());
        validatePrice(req.getListPrice(), req.getSalePrice());

        // 상품명과 슬러그는 관리자가 입력하지 않는다. 모델·세부모델명·색상으로 만든다.
        // CSV 일괄 등록만 값을 직접 넘겨 오므로 그때는 그대로 쓴다.
        String name = hasText(req.getName())
                ? req.getName()
                : buildName(req.getModel(), req.getSubModelName(), req.getColor());
        String slug = hasText(req.getSlug())
                ? req.getSlug()
                : uniqueSlug(buildSlug(req.getModelEn(), req.getSubModelNameEn(), req.getColorEn()));
        if (skuRepository.existsBySlug(slug)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        Artist artist = artistService.getArtistEntityByCode(req.getArtistCode());
        Sku sku = Sku.builder()
                .skuCode(codeGenerator.generateCode())
                .artist(artist)
                .name(name)
                .model(req.getModel())
                .subModelName(req.getSubModelName())
                .modelEn(req.getModelEn())
                .subModelNameEn(req.getSubModelNameEn())
                .color(req.getColor())
                .colorEn(req.getColorEn())
                .slug(slug)
                .description(req.getDescription())
                .skuType(req.getSkuType())
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
                .badges(req.getBadges())
                .primaryImageUrl(req.getPrimaryImageUrl())
                .widthCm(req.getWidthCm())
                .heightCm(req.getHeightCm())
                .depthCm(req.getDepthCm())
                .weightKg(req.getWeightKg())
                .weightG(req.getWeightG())
                .build();
        skuRepository.save(sku);
        skuReviewStatsRepository.save(SkuReviewStats.builder().sku(sku).build());
        return toSummary(sku);
    }

    @Transactional
    @CacheEvict(value = "sku360frames", key = "#skuCode")
    public SkuDto.SummaryResponse updateSku(String skuCode, SkuDto.UpdateRequest req) {
        Sku sku = getSkuEntityByCode(skuCode);
        categoryService.validateCodes(req.getMainCategory(), req.getGenre());
        validateEdition(req.getMainCategory(), req.getEditionSize(), req.getEditionNumber());
        validatePrice(req.getListPrice(), req.getSalePrice());

        // 슬러그는 URL 이라 바꾸면 기존 링크가 깨진다. 등록 때 만든 값을 유지한다.
        String name = buildName(req.getModel(), req.getSubModelName(), req.getColor());

        sku.update(name, sku.getSlug(), req.getDescription(),
                req.getSkuType(), req.getMainCategory(), req.getGenre(), req.getMaterial(),
                req.getMaterialDescription(), req.getPackagingTitle(), req.getPackagingDescription(),
                req.getListPrice(), req.getSalePrice(), req.getPrimaryImageUrl(),
                req.getEditionSize(), req.getEditionNumber(),
                req.getBadges(),
                req.getModel(), req.getSubModelName(),
                req.getModelEn(), req.getSubModelNameEn(), req.getColor(), req.getColorEn(),
                req.getWidthCm(), req.getHeightCm(), req.getDepthCm(),
                req.getWeightKg(), req.getWeightG());
        return toSummary(sku);
    }

    /** 할인가가 정가보다 클 수 없다. 같은 값은 허용한다(할인 없음). */
    private void validatePrice(BigDecimal listPrice, BigDecimal salePrice) {
        if (listPrice == null || salePrice == null) return;
        if (salePrice.compareTo(listPrice) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "할인가는 정가보다 클 수 없습니다.");
        }
    }

    private boolean hasText(String v) { return v != null && !v.isBlank(); }

    /** 화면에 보이는 상품명. 예) 닥쿤이 호돌이 검정 */
    private String buildName(String model, String subModelName, String color) {
        return Stream.of(model, subModelName, color)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    /**
     * URL 에 쓰는 슬러그. 영문명으로 만든다 —
     * 한글로 만들면 주소창에서 퍼센트 인코딩되어 읽을 수 없다.
     */
    private String buildSlug(String modelEn, String subModelNameEn, String colorEn) {
        String raw = Stream.of(modelEn, subModelNameEn, colorEn)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.joining("-"));
        String slug = raw.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "item" : slug;
    }

    /** 같은 모델의 다른 색이 같은 슬러그를 만들 수 있어 뒤에 번호를 붙인다. */
    private String uniqueSlug(String base) {
        if (!skuRepository.existsBySlug(base)) return base;
        for (int i = 2; i < 500; i++) {
            String candidate = base + "-" + i;
            if (!skuRepository.existsBySlug(candidate)) return candidate;
        }
        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
    }

    private void validateEdition(String mainCategory, Integer editionSize, Integer editionNumber) {
        boolean hasEdition = editionSize != null || editionNumber != null;
        if (hasEdition && !Sku.MAIN_LIMITED.equals(mainCategory)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "에디션 정보는 한정판 상품에만 입력할 수 있습니다.");
        }

        if (editionNumber != null && editionSize == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "에디션 번호를 입력하려면 총 수량도 함께 입력해야 합니다.");
        }
        if (editionSize != null && editionSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "에디션 총 수량은 1 이상이어야 합니다.");
        }
        if (editionNumber != null && (editionNumber <= 0 || editionNumber > editionSize)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "에디션 번호는 1 이상, 총 수량 이하여야 합니다.");
        }
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

    @Transactional
    @CacheEvict(value = "sku360frames", key = "#skuCode")
    public SkuDto.FrameListResponse upload360Frames(String skuCode, List<SkuDto.FrameUploadItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

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

        if (makePrimary) {
            sku.changePrimaryImage(fileUrl);
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

    public SkuDto.StockResponse getStock(String skuCode) {
        Sku sku = getSkuEntityByCode(skuCode);
        return SkuDto.StockResponse.builder()
                .skuCode(skuCode)
                .stockQuantity(stockService.getStock(sku.getId()))
                .build();
    }

    public Sku getSkuEntityByCode(String skuCode) {
        return skuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    public Sku getSkuEntityById(Long id) {
        return skuRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    private SkuDto.SummaryResponse toSummary(Sku sku) {
        int stock = stockService.getStock(sku.getId());
        SkuReviewStats stats = skuReviewStatsRepository.findById(sku.getId()).orElse(null);
        return SkuDto.SummaryResponse.from(sku, stock, stats);
    }

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
