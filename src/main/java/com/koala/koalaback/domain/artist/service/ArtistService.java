package com.koala.koalaback.domain.artist.service;

import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.entity.ArtistCareer;
import com.koala.koalaback.domain.artist.entity.ArtistFollow;
import com.koala.koalaback.domain.artist.entity.ArtistMedia;
import com.koala.koalaback.domain.artist.repository.ArtistCareerRepository;
import com.koala.koalaback.domain.artist.repository.ArtistFollowRepository;
import com.koala.koalaback.domain.artist.repository.ArtistMediaRepository;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.infra.storage.StorageUploader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistService {
    private final ArtistRepository artistRepository;
    private final ArtistMediaRepository artistMediaRepository;
    private final ArtistFollowRepository artistFollowRepository;
    private final ArtistCareerRepository artistCareerRepository;
    private final SkuRepository skuRepository;
    private final StorageUploader s3Uploader;
    private final CodeGenerator codeGenerator;

    public PageResponse<ArtistDto.SummaryResponse> getArtists(Pageable pageable) {
        Page<Artist> page = artistRepository.findByDeletedAtIsNullAndIsActiveTrue(pageable);
        List<Long> ids = page.getContent().stream().map(Artist::getId).toList();

        if (ids.isEmpty()) {
            return PageResponse.of(page.map(ArtistDto.SummaryResponse::from));
        }

        Map<Long, List<ArtistMedia>> mediaByArtist = artistMediaRepository
                .findByArtistIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getArtist().getId()));

        Map<Long, Long> followByArtist = artistFollowRepository
                .countsByArtistIds(ids)
                .stream()
                .collect(Collectors.toMap(
                        ArtistFollowRepository.FollowCountProjection::getArtistId,
                        ArtistFollowRepository.FollowCountProjection::getCnt
                ));

        List<Long> featuredSkuIds = page.getContent().stream()
                .map(Artist::getFeaturedSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Sku> featuredSkuMap = featuredSkuIds.isEmpty() ? Map.of() :
                skuRepository.findAllById(featuredSkuIds).stream()
                        .filter(s -> s.getDeletedAt() == null)
                        .collect(Collectors.toMap(Sku::getId, s -> s));

        return PageResponse.of(page.map(a -> ArtistDto.SummaryResponse.fromWithMedia(
                a,
                mediaByArtist.getOrDefault(a.getId(), List.of()),
                followByArtist.getOrDefault(a.getId(), 0L),
                a.getFeaturedSkuId() != null ? featuredSkuMap.get(a.getFeaturedSkuId()) : null
        )));
    }

    public ArtistDto.DetailResponse getArtist(String artistCode, Long userId) {
        Artist artist = getArtistEntityByCode(artistCode);
        List<ArtistMedia> media = artistMediaRepository
                .findByArtistIdOrderBySortOrderAsc(artist.getId());
        List<ArtistCareer> careers = artistCareerRepository
                .findByArtistIdOrderByCategoryAscSortOrderAsc(artist.getId());
        long followCount = artistFollowRepository.countByArtistId(artist.getId());
        boolean isFollowing = userId != null &&
                artistFollowRepository.existsByUserIdAndArtistId(userId, artist.getId());
        return ArtistDto.DetailResponse.from(artist, media, careers, followCount, isFollowing);
    }

    @Transactional
    public void follow(String artistCode, Long userId) {
        Artist artist = getArtistEntityByCode(artistCode);
        if (!artistFollowRepository.existsByUserIdAndArtistId(userId, artist.getId())) {
            artistFollowRepository.save(
                    ArtistFollow.builder().userId(userId).artist(artist).build());
        }
    }

    @Transactional
    public void unfollow(String artistCode, Long userId) {
        Artist artist = getArtistEntityByCode(artistCode);
        artistFollowRepository.findByUserIdAndArtistId(userId, artist.getId())
                .ifPresent(artistFollowRepository::delete);
    }

    public PageResponse<ArtistDto.SummaryResponse> getAdminArtists(Pageable pageable) {
        Page<Artist> page = artistRepository.findByDeletedAtIsNull(pageable);
        return PageResponse.of(page.map(ArtistDto.SummaryResponse::from));
    }

    @Transactional
    public ArtistDto.SummaryResponse createArtist(ArtistDto.CreateRequest req) {
        Artist artist = Artist.builder()
                .artistCode(codeGenerator.generateCode())
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .artistNote(req.getArtistNote())
                .profileImageUrl(req.getProfileImageUrl())
                .build();
        return ArtistDto.SummaryResponse.from(artistRepository.save(artist));
    }

    @Transactional
    public ArtistDto.SummaryResponse updateArtist(String artistCode, ArtistDto.UpdateRequest req) {
        Artist artist = getArtistEntityByCode(artistCode);
        artist.update(req.getName(), req.getSlug(),
                req.getDescription(), req.getArtistNote(), req.getProfileImageUrl());
        return ArtistDto.SummaryResponse.from(artist);
    }

    @Transactional
    public void activateArtist(String artistCode) {
        getArtistEntityByCode(artistCode).activate();
    }

    @Transactional
    public void deactivateArtist(String artistCode) {
        getArtistEntityByCode(artistCode).deactivate();
    }

    public List<ArtistDto.ArtistSkuItem> getArtistSkus(String artistCode) {
        Artist artist = getArtistEntityByCode(artistCode);
        return skuRepository.findByArtistIdAndDeletedAtIsNull(artist.getId())
                .stream()
                .map(ArtistDto.ArtistSkuItem::from)
                .toList();
    }

    @Transactional
    public ArtistDto.FeaturedSkuInfo setFeaturedSku(String artistCode, String skuCode) {
        Artist artist = getArtistEntityByCode(artistCode);
        Sku sku = skuRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        artist.setFeaturedSku(sku.getId());
        return ArtistDto.FeaturedSkuInfo.from(sku);
    }

    @Transactional
    public void clearFeaturedSku(String artistCode) {
        getArtistEntityByCode(artistCode).clearFeaturedSku();
    }

    @Transactional
    public void deleteArtist(String artistCode) {
        Artist artist = getArtistEntityByCode(artistCode);

        skuRepository.findByArtistIdAndDeletedAtIsNull(artist.getId())
                .forEach(sku -> sku.softDelete());

        artist.softDelete();
    }

    /**
     * 전시회 사진은 작가당 3장까지다.
     *
     * 전시 페이지가 작가를 가운데 두고 원형으로 배치하므로, 넘치면 원이
     * 겹친다. 어드민에서도 막지만 API 를 직접 부르는 경우가 있어 여기서도 막는다.
     */
    private static final String EXHIBITION_ROLE = "EXHIBITION";
    private static final int EXHIBITION_MAX = 5;

    private void checkExhibitionLimit(Long artistId, String mediaRole) {
        if (!EXHIBITION_ROLE.equals(mediaRole)) return;

        long current = artistMediaRepository.countByArtistIdAndMediaRole(artistId, EXHIBITION_ROLE);
        if (current >= EXHIBITION_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "전시회 사진은 작가당 " + EXHIBITION_MAX + "장까지 등록할 수 있습니다.");
        }
    }

    @Transactional
    public ArtistDto.MediaResponse addMedia(String artistCode,
                                            MultipartFile file,
                                            ArtistDto.MediaAddRequest req) {
        Artist artist = getArtistEntityByCode(artistCode);
        checkExhibitionLimit(artist.getId(), req.getMediaRole());

        String dir = "artists/" + artist.getArtistCode() + "/" + req.getMediaRole().toLowerCase();
        String fileUrl = s3Uploader.upload(file, dir);

        int nextOrder = req.getSortOrder() != null ? req.getSortOrder()
                : artistMediaRepository.findByArtistIdOrderBySortOrderAsc(artist.getId()).size();

        ArtistMedia media = ArtistMedia.builder()
                .artist(artist)
                .mediaType(req.getMediaType())
                .mediaRole(req.getMediaRole())
                .fileUrl(fileUrl)
                .thumbnailUrl(req.getThumbnailUrl())
                .title(req.getTitle())
                .sortOrder(nextOrder)
                .build();
        artistMediaRepository.save(media);

        if ("PROFILE".equals(req.getMediaRole())) {
            artist.updateProfileImage(fileUrl);
        }

        return ArtistDto.MediaResponse.from(media);
    }

    @Transactional
    public ArtistDto.MediaResponse addMediaUrl(String artistCode, ArtistDto.MediaUrlRequest req) {
        Artist artist = getArtistEntityByCode(artistCode);

        artistMediaRepository.deleteByArtistIdAndMediaRole(artist.getId(), req.getMediaRole());

        int order = req.getSortOrder() != null ? req.getSortOrder() : 0;
        ArtistMedia media = ArtistMedia.builder()
                .artist(artist)
                .mediaType(req.getMediaType())
                .mediaRole(req.getMediaRole())
                .fileUrl(req.getFileUrl())
                .thumbnailUrl(req.getThumbnailUrl())
                .title(req.getTitle())
                .sortOrder(order)
                .build();
        artistMediaRepository.save(media);
        return ArtistDto.MediaResponse.from(media);
    }

    @Transactional
    public ArtistDto.MediaResponse updateMediaThumbnail(String artistCode, Long mediaId, String thumbnailUrl) {
        Artist artist = getArtistEntityByCode(artistCode);
        ArtistMedia media = artistMediaRepository.findById(mediaId)
                .filter(m -> m.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        media.updateThumbnail(thumbnailUrl);
        return ArtistDto.MediaResponse.from(media);
    }

    @Transactional
    public void deleteMedia(String artistCode, Long mediaId) {
        Artist artist = getArtistEntityByCode(artistCode);
        ArtistMedia media = artistMediaRepository.findById(mediaId)
                .filter(m -> m.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        s3Uploader.delete(media.getFileUrl());
        artistMediaRepository.delete(media);
    }

    public List<ArtistDto.MediaResponse> getMediaList(String artistCode) {
        Artist artist = getArtistEntityByCode(artistCode);
        return artistMediaRepository
                .findByArtistIdOrderBySortOrderAsc(artist.getId())
                .stream().map(ArtistDto.MediaResponse::from).toList();
    }

    @Transactional
    public ArtistDto.CareerResponse addCareer(String artistCode, ArtistDto.CareerAddRequest req) {
        Artist artist = getArtistEntityByCode(artistCode);
        int nextOrder = req.getSortOrder() != null ? req.getSortOrder()
                : artistCareerRepository.findByArtistIdOrderByCategoryAscSortOrderAsc(artist.getId()).size();

        ArtistCareer career = ArtistCareer.builder()
                .artist(artist)
                .category(req.getCategory())
                .year(req.getYear())
                .content(req.getContent())
                .sortOrder(nextOrder)
                .build();
        return ArtistDto.CareerResponse.from(artistCareerRepository.save(career));
    }

    @Transactional
    public ArtistDto.CareerResponse updateCareer(String artistCode, Long careerId,
                                                  ArtistDto.CareerUpdateRequest req) {
        Artist artist = getArtistEntityByCode(artistCode);
        ArtistCareer career = artistCareerRepository.findById(careerId)
                .filter(c -> c.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        career.update(req.getCategory(), req.getYear(), req.getContent(),
                req.getSortOrder() != null ? req.getSortOrder() : career.getSortOrder());
        return ArtistDto.CareerResponse.from(career);
    }

    @Transactional
    public void deleteCareer(String artistCode, Long careerId) {
        Artist artist = getArtistEntityByCode(artistCode);
        ArtistCareer career = artistCareerRepository.findById(careerId)
                .filter(c -> c.getArtist().getId().equals(artist.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        artistCareerRepository.delete(career);
    }

    public Artist getArtistEntityByCode(String artistCode) {
        return artistRepository.findByArtistCode(artistCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    public boolean isFollowing(String artistCode, Long userId){
        if (userId == null) return false;
        Artist artist = getArtistEntityByCode(artistCode);
        return artistFollowRepository.existsByUserIdAndArtistId(userId, artist.getId());
    }
}
