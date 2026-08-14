package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/artists")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminArtistController {
    private final ArtistService artistService;

    @GetMapping
    public ApiResponse<PageResponse<ArtistDto.SummaryResponse>> getArtists(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(artistService.getAdminArtists(
                PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.SummaryResponse> createArtist(
            @Valid @RequestBody ArtistDto.CreateRequest req) {
        return ApiResponse.ok(artistService.createArtist(req));
    }

    @PutMapping("/{artistCode}")
    public ApiResponse<ArtistDto.SummaryResponse> updateArtist(
            @PathVariable String artistCode,
            @Valid @RequestBody ArtistDto.UpdateRequest req) {
        return ApiResponse.ok(artistService.updateArtist(artistCode, req));
    }

    @PatchMapping("/{artistCode}/activate")
    public ApiResponse<Void> activateArtist(@PathVariable String artistCode) {
        artistService.activateArtist(artistCode);
        return ApiResponse.ok();
    }

    @PatchMapping("/{artistCode}/deactivate")
    public ApiResponse<Void> deactivateArtist(@PathVariable String artistCode) {
        artistService.deactivateArtist(artistCode);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{artistCode}")
    public ApiResponse<Void> deleteArtist(@PathVariable String artistCode) {
        artistService.deleteArtist(artistCode);
        return ApiResponse.ok();
    }

    @GetMapping("/{artistCode}/skus")
    public ApiResponse<List<ArtistDto.ArtistSkuItem>> getArtistSkus(
            @PathVariable String artistCode) {
        return ApiResponse.ok(artistService.getArtistSkus(artistCode));
    }

    @PutMapping("/{artistCode}/featured-sku")
    public ApiResponse<ArtistDto.FeaturedSkuInfo> setFeaturedSku(
            @PathVariable String artistCode,
            @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(artistService.setFeaturedSku(artistCode, body.get("skuCode")));
    }

    @DeleteMapping("/{artistCode}/featured-sku")
    public ApiResponse<Void> clearFeaturedSku(@PathVariable String artistCode) {
        artistService.clearFeaturedSku(artistCode);
        return ApiResponse.ok();
    }

    @GetMapping("/{artistCode}/media")
    public ApiResponse<List<ArtistDto.MediaResponse>> getMedia(
            @PathVariable String artistCode) {
        return ApiResponse.ok(artistService.getMediaList(artistCode));
    }

    @PostMapping(value = "/{artistCode}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.MediaResponse> addMedia(
            @PathVariable String artistCode,
            @RequestPart("file") MultipartFile file,
            @RequestPart("meta") @Valid ArtistDto.MediaAddRequest req) {
        return ApiResponse.ok(artistService.addMedia(artistCode, file, req));
    }

    @PostMapping("/{artistCode}/media-url")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.MediaResponse> addMediaUrl(
            @PathVariable String artistCode,
            @Valid @RequestBody ArtistDto.MediaUrlRequest req) {
        return ApiResponse.ok(artistService.addMediaUrl(artistCode, req));
    }

    @DeleteMapping("/{artistCode}/media/{mediaId}")
    public ApiResponse<Void> deleteMedia(
            @PathVariable String artistCode,
            @PathVariable Long mediaId) {
        artistService.deleteMedia(artistCode, mediaId);
        return ApiResponse.ok();
    }

    @PostMapping("/{artistCode}/careers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ArtistDto.CareerResponse> addCareer(
            @PathVariable String artistCode,
            @Valid @RequestBody ArtistDto.CareerAddRequest req) {
        return ApiResponse.ok(artistService.addCareer(artistCode, req));
    }

    @PutMapping("/{artistCode}/careers/{careerId}")
    public ApiResponse<ArtistDto.CareerResponse> updateCareer(
            @PathVariable String artistCode,
            @PathVariable Long careerId,
            @Valid @RequestBody ArtistDto.CareerUpdateRequest req) {
        return ApiResponse.ok(artistService.updateCareer(artistCode, careerId, req));
    }

    @DeleteMapping("/{artistCode}/careers/{careerId}")
    public ApiResponse<Void> deleteCareer(
            @PathVariable String artistCode,
            @PathVariable Long careerId) {
        artistService.deleteCareer(artistCode, careerId);
        return ApiResponse.ok();
    }
}
