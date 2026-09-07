package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "어드민 · 작가", description = "작가 등록·수정, 대표 작품, 사진, 약력")
@RestController
@RequestMapping("/admin/api/v1/artists")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminArtistController {
    private final ArtistService artistService;

    @Operation(summary = "작가 목록 (어드민)", description = "비활성 작가까지 보인다.")
    @GetMapping
    public ApiResponse<PageResponse<ArtistDto.SummaryResponse>> getArtists(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(artistService.getAdminArtists(
                PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @Operation(summary = "작가 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.SummaryResponse> createArtist(
            @Valid @RequestBody ArtistDto.CreateRequest req) {
        return ApiResponse.ok(artistService.createArtist(req));
    }

    @Operation(summary = "작가 수정")
    @PutMapping("/{artistCode}")
    public ApiResponse<ArtistDto.SummaryResponse> updateArtist(
            @PathVariable String artistCode,
            @Valid @RequestBody ArtistDto.UpdateRequest req) {
        return ApiResponse.ok(artistService.updateArtist(artistCode, req));
    }

    @Operation(summary = "작가 활성화", description = "고객 화면에 다시 보이게 한다.")
    @PatchMapping("/{artistCode}/activate")
    public ApiResponse<Void> activateArtist(@PathVariable String artistCode) {
        artistService.activateArtist(artistCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "작가 비활성화", description = "고객 화면에서 감춘다. 작품과 자료는 그대로 남는다.")
    @PatchMapping("/{artistCode}/deactivate")
    public ApiResponse<Void> deactivateArtist(@PathVariable String artistCode) {
        artistService.deactivateArtist(artistCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "작가 삭제", description = """
            소프트 삭제다. 이 작가의 작품도 함께 삭제 표시된다 — 작가가 사라졌는데
            작품만 남으면 목록에 주인 없는 작품이 걸린다.

            행을 실제로 지우지는 않는다. 지나간 주문이 이 작품들을 가리키고 있다.
            """)
    @DeleteMapping("/{artistCode}")
    public ApiResponse<Void> deleteArtist(@PathVariable String artistCode) {
        artistService.deleteArtist(artistCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "작가의 작품 목록", description = """
            대표 작품을 고를 때 쓴다. 가격은 부가세를 더한 값으로 내려간다 — 어드민에서
            보는 숫자와 고객이 보는 숫자를 맞춘다.
            """)
    @GetMapping("/{artistCode}/skus")
    public ApiResponse<List<ArtistDto.ArtistSkuItem>> getArtistSkus(
            @PathVariable String artistCode) {
        return ApiResponse.ok(artistService.getArtistSkus(artistCode));
    }

    @Operation(summary = "대표 작품 지정", description = "작가 카드에 걸리는 한 점을 정한다.")
    @PutMapping("/{artistCode}/featured-sku")
    public ApiResponse<ArtistDto.FeaturedSkuInfo> setFeaturedSku(
            @PathVariable String artistCode,
            @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(artistService.setFeaturedSku(artistCode, body.get("skuCode")));
    }

    @Operation(summary = "대표 작품 해제")
    @DeleteMapping("/{artistCode}/featured-sku")
    public ApiResponse<Void> clearFeaturedSku(@PathVariable String artistCode) {
        artistService.clearFeaturedSku(artistCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "작가 사진 목록")
    @GetMapping("/{artistCode}/media")
    public ApiResponse<List<ArtistDto.MediaResponse>> getMedia(
            @PathVariable String artistCode) {
        return ApiResponse.ok(artistService.getMediaList(artistCode));
    }

    @Operation(summary = "작가 사진 등록 (업로드)", description = """
            파일을 올려 S3 에 저장한다.

            역할이 EXHIBITION 인 사진은 작가당 5장까지다. 전시 페이지가 작가를 가운데
            두고 원형으로 배치하므로 넘치면 원이 겹친다. 어드민 화면에서도 막지만 API 를
            직접 부르는 경우가 있어 여기서도 막는다.
            """)
    @PostMapping(value = "/{artistCode}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.MediaResponse> addMedia(
            @PathVariable String artistCode,
            @RequestPart("file") MultipartFile file,
            @RequestPart("meta") @Valid ArtistDto.MediaAddRequest req) {
        return ApiResponse.ok(artistService.addMedia(artistCode, file, req));
    }

    @Operation(summary = "작가 사진 등록 (주소)", description = """
            이미 올라가 있는 파일의 주소를 등록한다.

            업로드와 달리 같은 역할의 기존 사진을 지우고 새로 넣는다 — 이 경로는 한
            역할에 한 장을 두는 자리다.
            """)
    @PostMapping("/{artistCode}/media-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.MediaResponse> addMediaUrl(
            @PathVariable String artistCode,
            @Valid @RequestBody ArtistDto.MediaUrlRequest req) {
        return ApiResponse.ok(artistService.addMediaUrl(artistCode, req));
    }

    @Operation(summary = "사진 썸네일 변경", description = "이 작가의 사진인지 확인한 뒤 바꾼다.")
    @PatchMapping("/{artistCode}/media/{mediaId}/thumbnail")
    public ApiResponse<ArtistDto.MediaResponse> updateMediaThumbnail(
            @PathVariable String artistCode,
            @PathVariable Long mediaId,
            @Valid @RequestBody ArtistDto.MediaThumbnailRequest req) {
        return ApiResponse.ok(artistService.updateMediaThumbnail(artistCode, mediaId, req.getThumbnailUrl()));
    }

    @Operation(summary = "작가 사진 삭제")
    @DeleteMapping("/{artistCode}/media/{mediaId}")
    public ApiResponse<Void> deleteMedia(
            @PathVariable String artistCode,
            @PathVariable Long mediaId) {
        artistService.deleteMedia(artistCode, mediaId);
        return ApiResponse.ok();
    }

    @Operation(summary = "약력 추가")
    @PostMapping("/{artistCode}/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.CareerResponse> addCareer(
            @PathVariable String artistCode,
            @Valid @RequestBody ArtistDto.CareerAddRequest req) {
        return ApiResponse.ok(artistService.addCareer(artistCode, req));
    }

    @Operation(summary = "약력 수정")
    @PutMapping("/{artistCode}/careers/{careerId}")
    public ApiResponse<ArtistDto.CareerResponse> updateCareer(
            @PathVariable String artistCode,
            @PathVariable Long careerId,
            @Valid @RequestBody ArtistDto.CareerUpdateRequest req) {
        return ApiResponse.ok(artistService.updateCareer(artistCode, careerId, req));
    }

    @Operation(summary = "약력 삭제")
    @DeleteMapping("/{artistCode}/careers/{careerId}")
    public ApiResponse<Void> deleteCareer(
            @PathVariable String artistCode,
            @PathVariable Long careerId) {
        artistService.deleteCareer(artistCode, careerId);
        return ApiResponse.ok();
    }
}
