package com.koala.koalaback.api.artist;

import com.koala.koalaback.domain.artist.dto.ArtistDto;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "작가", description = "작가 목록·상세와 팔로우")
@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {
    private final ArtistService artistService;

    @Operation(summary = "작가 목록")
    @GetMapping
    public ApiResponse<PageResponse<ArtistDto.SummaryResponse>> getArtists(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(artistService.getArtists(pageable));
    }

    @Operation(summary = "작가 상세")
    @GetMapping("/{artistCode}")
    public ApiResponse<ArtistDto.DetailResponse> getArtist(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(artistService.getArtist(artistCode, userId));
    }
    @Operation(summary = "팔로우 여부", description = """
            로그인이 필요하다. 목록·상세와 달리 이 경로만 인증을 요구한다 — 누가 보고
            있는지 알아야 답할 수 있다.
            """)
    @GetMapping("/{artistCode}/following")
    public ApiResponse<Boolean> getFollowStatus(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
            return ApiResponse.ok(artistService.isFollowing(artistCode, userId));
    }
    @Operation(summary = "작가 팔로우")
    @PostMapping("/{artistCode}/follow")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> follow(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
        artistService.follow(artistCode, userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "팔로우 해제")
    @DeleteMapping("/{artistCode}/follow")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> unfollow(
            @PathVariable String artistCode,
            @AuthenticationPrincipal Long userId) {
        artistService.unfollow(artistCode, userId);
        return ApiResponse.ok();
    }
}
