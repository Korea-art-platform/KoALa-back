package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.service.OnSiteAccessService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "어드민 · 현장결제 설정", description = "현장결제 페이지 켜기·끄기와 PIN")
@RestController
@RequestMapping("/admin/api/v1/onsite-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOnSiteSettingController {
    private final OnSiteAccessService onSiteAccessService;

    @Operation(summary = "현장결제 페이지 설정 조회")
    @GetMapping
    public ApiResponse<OnSitePaymentDto.SettingResponse> get() {
        return ApiResponse.ok(onSiteAccessService.getSetting());
    }

    @Operation(summary = "현장결제 페이지 켜기·끄기, PIN 변경", description = """
            PIN 은 숫자 6자리. 켜려면 PIN 이 정해져 있어야 한다. 끄거나 PIN 을 바꾸면
            현장에서 쓰던 입장권은 모두 바로 막힌다.
            """)
    @PutMapping
    public ApiResponse<OnSitePaymentDto.SettingResponse> update(
            @Valid @RequestBody OnSitePaymentDto.SettingUpdateRequest req) {
        return ApiResponse.ok(onSiteAccessService.updateSetting(req));
    }
}
