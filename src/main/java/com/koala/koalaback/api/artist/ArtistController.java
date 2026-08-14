package com.koala.koalaback.api.artist;

import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {
    private final ArtistService artistService;

    @GetMapping
    public ApiResponse<PageResponse<ArtistDto.SummaryResponse>> getArtists(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(artistService.getArtists(pageable));
    }

    @GetMapping("/{artistCode}")
    public ApiResponse<ArtistDto.DetailResponse> getArtist(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(artistService.getArtist(artistCode, userId));
    }
    @GetMapping("/{artistCode}/following")
    public ApiResponse<Boolean> getFollowStatus(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
            return ApiResponse.ok(artistService.isFollowing(artistCode, userId));
    }
    @PostMapping("/{artistCode}/follow")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> follow(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
        artistService.follow(artistCode, userId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{artistCode}/follow")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> unfollow(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
        artistService.unfollow(artistCode, userId);
        return ApiResponse.ok();
    }
}
