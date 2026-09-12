package com.koala.koalaback.domain.banner.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.entity.Banner;
import com.koala.koalaback.domain.banner.repository.BannerRepository;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.infra.storage.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {
    private final BannerRepository bannerRepository;
    private final SkuRepository skuRepository;
    private final VatPolicy vatPolicy;
    private final AdminService adminService;
    private final CodeGenerator codeGenerator;
    private final StorageUploader storageUploader;

    public List<BannerDto.BannerResponse> getVisibleBanners(String bannerType) {
        return toResponses(bannerRepository.findVisibleByType(bannerType, LocalDateTime.now()));
    }

    public List<BannerDto.BannerResponse> getAllBanners() {
        return toResponses(bannerRepository.findByDeletedAtIsNullOrderBySortOrderAsc());
    }

    public BannerDto.BannerResponse getBanner(String bannerCode) {
        return toResponse(getBannerEntityByCode(bannerCode));
    }

    @Transactional
    public BannerDto.BannerResponse createBanner(Long adminId,
                                                 BannerDto.CreateRequest req) {
        Admin admin = adminService.getAdminById(adminId);

        Banner banner = Banner.builder()
                .bannerCode(codeGenerator.generateCode())
                .bannerType(req.getBannerType())
                .title(req.getTitle())
                .subtitle(req.getSubtitle())
                .badge(req.getBadge())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .mobileImageUrl(req.getMobileImageUrl())
                .videoUrl(req.getVideoUrl())
                .sku(findSku(req.getSkuCode()))
                .effectImageUrl1(req.getEffectImageUrl1())
                .effectImageUrl2(req.getEffectImageUrl2())
                .effectImageUrl3(req.getEffectImageUrl3())
                .linkUrl(req.getLinkUrl())
                .linkTarget(req.getLinkTarget())
                .bgColor(req.getBgColor())
                .textColor(req.getTextColor())
                .sortOrder(req.getSortOrder())
                .visibleFrom(req.getVisibleFrom())
                .visibleTo(req.getVisibleTo())
                .createdByAdmin(admin)
                .build();

        return toResponse(bannerRepository.save(banner));
    }

    @Transactional
    public BannerDto.BannerResponse updateBanner(Long adminId, String bannerCode,
                                                 BannerDto.UpdateRequest req) {
        Admin admin = adminService.getAdminById(adminId);
        Banner banner = getBannerEntityByCode(bannerCode);

        banner.update(req.getTitle(), req.getSubtitle(),
                req.getBadge(), req.getDescription(),
                req.getImageUrl(), req.getMobileImageUrl(), req.getVideoUrl(),
                findSku(req.getSkuCode()),
                req.getEffectImageUrl1(), req.getEffectImageUrl2(), req.getEffectImageUrl3(),
                req.getLinkUrl(), req.getLinkTarget(),
                req.getBgColor(), req.getTextColor(),
                req.getSortOrder(), req.getVisibleFrom(),
                req.getVisibleTo(), admin);

        return toResponse(banner);
    }

    @Transactional
    public void activateBanner(String bannerCode) {
        getBannerEntityByCode(bannerCode).activate();
    }

    @Transactional
    public void deactivateBanner(String bannerCode) {
        getBannerEntityByCode(bannerCode).deactivate();
    }

    @Transactional
    public void deleteBanner(String bannerCode) {
        getBannerEntityByCode(bannerCode).softDelete();
    }

    public String uploadImage(MultipartFile file) {
        return storageUploader.upload(file, "banners");
    }

    @Transactional
    public BannerDto.BannerResponse updateImage(String bannerCode, MultipartFile file) {
        Banner banner = getBannerEntityByCode(bannerCode);

        if (banner.getImageUrl() != null && !banner.getImageUrl().isBlank()) {
            storageUploader.delete(banner.getImageUrl());
        }
        String newUrl = storageUploader.upload(file, "banners");
        banner.updateImageUrl(newUrl);
        return toResponse(banner);
    }

    private Banner getBannerEntityByCode(String bannerCode) {
        return bannerRepository.findByBannerCode(bannerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 히어로 작품 조회 — 비우면 연결 안 함
    private Sku findSku(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) return null;
        return skuRepository.findBySkuCode(skuCode)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.SKU_NOT_FOUND));
    }

    // 작품이 걸린 배너가 있을 때만 면세 분류를 읽는다
    private List<BannerDto.BannerResponse> toResponses(List<Banner> banners) {
        Set<String> exempt = banners.stream().anyMatch(b -> b.getSku() != null)
                ? vatPolicy.exemptMainCategories()
                : Set.of();
        return banners.stream()
                .map(b -> BannerDto.BannerResponse.from(b, vatPolicy, exempt))
                .toList();
    }

    private BannerDto.BannerResponse toResponse(Banner banner) {
        return toResponses(List.of(banner)).get(0);
    }
}
