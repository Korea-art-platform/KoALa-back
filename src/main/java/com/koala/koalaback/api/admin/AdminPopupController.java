package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.popup.dto.PopupDto;
import com.koala.koalaback.domain.popup.service.PopupService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "어드민 · 팝업", description = "고객 화면 팝업(프로모션 모달) 등록·노출·이미지")
@RestController
@RequestMapping("/admin/api/v1/popups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPopupController {
    private final PopupService popupService;

    @Operation(summary = "팝업 목록 (어드민)", description = """
            노출 여부와 무관하게 삭제되지 않은 팝업을 전부 내려준다.
            정렬 순서 오름차순, 같으면 최근 등록순이다.
            """)
    @GetMapping
    public ApiResponse<List<PopupDto.PopupResponse>> getAllPopups() {
        return ApiResponse.ok(popupService.getAllPopups());
    }

    @Operation(summary = "팝업 상세")
    @GetMapping("/{popupCode}")
    public ApiResponse<PopupDto.PopupResponse> getPopup(@PathVariable String popupCode) {
        return ApiResponse.ok(popupService.getPopup(popupCode));
    }

    @Operation(summary = "팝업 등록", description = """
            이미지 방식이면 imageUrl 이 필요하고, 바로가기 버튼을 켜면 landingUrl 이 필요하다.
            landingUrl 은 http(s) 절대 주소나 "/" 로 시작하는 내부 경로만 받는다.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PopupDto.PopupResponse> createPopup(
            @AuthenticationPrincipal Long adminId,
            @RequestBody PopupDto.PopupRequest req) {
        return ApiResponse.ok(popupService.createPopup(adminId, req));
    }

    @Operation(summary = "팝업 수정", description = "검사 규칙은 등록과 같다.")
    @PutMapping("/{popupCode}")
    public ApiResponse<PopupDto.PopupResponse> updatePopup(
            @AuthenticationPrincipal Long adminId,
            @PathVariable String popupCode,
            @RequestBody PopupDto.PopupRequest req) {
        return ApiResponse.ok(popupService.updatePopup(adminId, popupCode, req));
    }

    @Operation(summary = "팝업 활성화", description = "고객 화면에 뜬다.")
    @PatchMapping("/{popupCode}/activate")
    public ApiResponse<Void> activatePopup(@PathVariable String popupCode) {
        popupService.activatePopup(popupCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "팝업 비활성화")
    @PatchMapping("/{popupCode}/deactivate")
    public ApiResponse<Void> deactivatePopup(@PathVariable String popupCode) {
        popupService.deactivatePopup(popupCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "팝업 삭제", description = "소프트 삭제다.")
    @DeleteMapping("/{popupCode}")
    public ApiResponse<Void> deletePopup(@PathVariable String popupCode) {
        popupService.deletePopup(popupCode);
        return ApiResponse.ok();
    }

    @Operation(summary = "팝업 이미지 업로드", description = """
            파일만 올리고 주소를 돌려준다. 팝업에 붙이지는 않는다 — 등록·수정 요청에
            그 주소를 실어 보내는 방식이다.
            """)
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, String>> uploadPopupImage(
            @RequestPart("file") MultipartFile file) {
        String url = popupService.uploadImage(file);
        return ApiResponse.ok(Map.of("imageUrl", url));
    }
}
