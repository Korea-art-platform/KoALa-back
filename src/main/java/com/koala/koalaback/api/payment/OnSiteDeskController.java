package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.service.OnSiteAccessService;
import com.koala.koalaback.domain.order.service.OnSitePaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "현장결제 페이지", description = "관리자 계정 없이 PIN 으로 들어가 결제 링크를 만든다")
@RestController
@RequestMapping("/api/v1/onsite")
@RequiredArgsConstructor
public class OnSiteDeskController {
    public static final String SESSION_HEADER = "X-Onsite-Session";

    private final OnSiteAccessService onSiteAccessService;
    private final OnSitePaymentService onSitePaymentService;

    @Operation(summary = "현장결제 페이지가 켜져 있는지")
    @GetMapping("/status")
    public ApiResponse<OnSitePaymentDto.StatusResponse> status() {
        return ApiResponse.ok(onSiteAccessService.status());
    }

    @Operation(summary = "PIN 으로 입장", description = """
            맞으면 12시간 동안 쓰는 입장권을 준다. 관리자가 끄거나 PIN 을 바꾸면
            이전 입장권은 바로 막힌다. 같은 IP 에서 1분에 5번까지만 시도할 수 있다.
            """)
    @PostMapping("/session")
    public ApiResponse<OnSitePaymentDto.SessionResponse> openSession(
            @Valid @RequestBody OnSitePaymentDto.PinRequest req) {
        return ApiResponse.ok(onSiteAccessService.openSession(req.getPin()));
    }

    @Operation(summary = "현장결제 만들기", description = "헤더 X-Onsite-Session 에 입장권을 싣는다")
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OnSitePaymentDto.Response> create(
            @RequestHeader(value = SESSION_HEADER, required = false) String session,
            @Valid @RequestBody OnSitePaymentDto.CreateRequest req) {
        onSiteAccessService.verifySession(session);
        return ApiResponse.ok(onSitePaymentService.create(req));
    }

    @Operation(summary = "최근 현장결제 50건", description = "헤더 X-Onsite-Session 에 입장권을 싣는다")
    @GetMapping("/payments")
    public ApiResponse<List<OnSitePaymentDto.Response>> getRecent(
            @RequestHeader(value = SESSION_HEADER, required = false) String session) {
        onSiteAccessService.verifySession(session);
        return ApiResponse.ok(onSitePaymentService.getRecent());
    }
}
