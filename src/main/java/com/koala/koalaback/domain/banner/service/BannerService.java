package com.koala.koalaback.domain.banner.service;

import com.koala.koalaback.domain.admin.entity.Admin;
import com.koala.koalaback.domain.admin.service.AdminService;
import com.koala.koalaback.domain.banner.dto.BannerDto;
import com.koala.koalaback.domain.banner.entity.Banner;
import com.koala.koalaback.domain.banner.repository.BannerRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {
    private final BannerRepository bannerRepository;
    private final AdminService adminService;
    private final CodeGenerator codeGenerator;
    private final StorageUploader storageUploader;

    public List<BannerDto.BannerResponse> getVisibleBanners(String bannerType) {
        return bannerRepository
                .findVisibleByType(bannerType, LocalDateTime.now())
                .stream()
                .map(BannerDto.BannerResponse::from)
                .toList();
    }

    public List<BannerDto.BannerResponse> getAllBanners() {
        return bannerRepository.findByDeletedAtIsNullOrderBySortOrderAsc()
                .stream()
                .map(BannerDto.BannerResponse::from)
                .toList();
    }

    public BannerDto.BannerResponse getBanner(String bannerCode) {
        return BannerDto.BannerResponse.from(getBannerEntityByCode(bannerCode));
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
                .linkUrl(req.getLinkUrl())
                .linkTarget(req.getLinkTarget())
                .bgColor(req.getBgColor())
                .textColor(req.getTextColor())
                .sortOrder(req.getSortOrder())
                .visibleFrom(req.getVisibleFrom())
                .visibleTo(req.getVisibleTo())
                .createdByAdmin(admin)
                .build();

        return BannerDto.BannerResponse.from(bannerRepository.save(banner));
    }

    @Transactional
    public BannerDto.BannerResponse updateBanner(Long adminId, String bannerCode,
                                                 BannerDto.UpdateRequest req) {
        Admin admin = adminService.getAdminById(adminId);
        Banner banner = getBannerEntityByCode(bannerCode);

        banner.update(req.getTitle(), req.getSubtitle(),
                req.getBadge(), req.getDescription(),
                req.getImageUrl(), req.getMobileImageUrl(), req.getVideoUrl(),
                req.getLinkUrl(), req.getLinkTarget(),
                req.getBgColor(), req.getTextColor(),
                req.getSortOrder(), req.getVisibleFrom(),
                req.getVisibleTo(), admin);

        return BannerDto.BannerResponse.from(banner);
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
        return BannerDto.BannerResponse.from(banner);
    }

    private Banner getBannerEntityByCode(String bannerCode) {
        return bannerRepository.findByBannerCode(bannerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
