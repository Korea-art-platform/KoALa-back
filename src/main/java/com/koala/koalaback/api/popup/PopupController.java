package com.koala.koalaback.api.popup;

import com.koala.koalaback.domain.popup.dto.PopupDto;
import com.koala.koalaback.domain.popup.service.PopupService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "팝업", description = "고객 화면 팝업(프로모션 모달)")
@RestController
@RequestMapping("/api/v1/popups")
@RequiredArgsConstructor
public class PopupController {
    private final PopupService popupService;

    @Operation(summary = "노출 중인 팝업", description = """
            활성이고 삭제되지 않은 팝업 중 해당 언어의 것만 정렬 순서대로 내려간다.
            page=home 이면 HOME·ALL 위치 팝업을, 그 밖의 값이거나 비우면 ALL 위치 팝업만 준다.
            """)
    @GetMapping
    public ApiResponse<List<PopupDto.PublicPopupResponse>> getVisiblePopups(
            @Parameter(description = "ko 또는 en. 그 밖의 값은 ko 로 본다")
            @RequestParam(defaultValue = "ko") String lang,
            @Parameter(description = "home 이면 홈 전용 팝업까지 포함")
            @RequestParam(required = false) String page) {
        return ApiResponse.ok(popupService.getVisiblePopups(lang, page));
    }
}
