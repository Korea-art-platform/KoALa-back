package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "결제", description = "결제 준비·승인, 그리고 어드민 환불·수동 종결")
@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @Operation(summary = "결제 준비", description = """
            결제 건을 만들고 청구 금액을 확정한다. 청구액은 요청이 아니라 주문의
            totalAmount 를 그대로 쓴다 — 요청 금액을 믿으면 값을 낮춰 보내는 것만으로
            싸게 살 수 있다.

            PENDING_PAYMENT 상태의 주문만 준비할 수 있다. 이미 결제된 주문에 다시
            부르면 ORDER_ALREADY_PAID 로 막힌다.

            비로그인도 부를 수 있다(SecurityConfig permitAll). 비회원 주문에는 주인이
            없어 주문번호를 아는 사람이면 결제를 시작할 수 있지만, 그래 봐야 남의 주문을
            대신 내주는 것이고 승인에는 PG 가 준 결제키가 있어야 한다. 반대로 회원 주문은
            그 회원만, 비회원 주문은 로그인하지 않은 쪽만 만질 수 있다.
            """)
    @PostMapping("/api/v1/payments/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentDto.PrepareResponse> prepare(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaymentDto.PrepareRequest req) {
        return ApiResponse.ok(paymentService.prepare(userId, req));
    }

    @Operation(summary = "결제 승인", description = """
            PG 에 승인을 요청하고 결과를 반영한다. 토스처럼 프론트가 승인을 부르는
            방식에서 쓴다. 나이스·페이플은 서버가 리턴 URL 로 받아 처리하므로 이 경로를
            타지 않는다.

            승인 전에 저장된 요청 금액과 대조한다. 다르면 PAYMENT_AMOUNT_MISMATCH 로
            막는다. READY 상태가 아니면 진행하지 않는다 — 이미 진행 중이면
            PAYMENT_IN_PROGRESS, 미확정이면 PAYMENT_IN_DOUBT.

            PG 호출이 예외로 끝나면 실패로 단정하지 않고 '미확정(IN_DOUBT)' 으로 둔다.
            승인이 됐는데 실패로 처리하면 돈은 빠져나가고 주문은 남지 않는다. 미확정 건은
            관리자에게 알리고, 웹훅이 오면 그것으로 확정하거나 어드민이 직접 종결한다.
            """)
    @PostMapping("/api/v1/payments/confirm")
    public ApiResponse<PaymentDto.PaymentResponse> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PaymentDto.ConfirmRequest req) {
        return ApiResponse.ok(paymentService.confirm(userId, req));
    }

    @Operation(summary = "결제 취소·환불 (어드민)", description = """
            승인(CAPTURED)된 결제만 취소할 수 있다. cancelAmount 를 비우면 승인 금액
            전액을 환불한다.

            환불 금액은 1원 이상이어야 하고 승인 금액을 넘을 수 없다. 넘으면
            INVALID_INPUT 으로 거절한다.

            PG 취소 호출이 예외로 끝나면 취소된 것으로도 실패한 것으로도 단정하지 않고
            미확정으로 두고 관리자에게 알린다.
            """)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/api/v1/payments/{paymentNo}/cancel")
    public ApiResponse<PaymentDto.PaymentResponse> cancel(
            @PathVariable String paymentNo,
            @Valid @RequestBody PaymentDto.CancelRequest req) {
        return ApiResponse.ok(paymentService.cancel(paymentNo, req));
    }

    @Operation(summary = "미확정 결제 수동 종결 (어드민)", description = """
            승인 여부를 끝내 알 수 없는 건을 사람이 PG 콘솔에서 확인하고 닫는다.
            outcome 으로 결과를 지정한다.

            미확정(IN_DOUBT 등) 또는 CANCEL_IN_PROGRESS 인 건만 종결할 수 있다. 이미
            끝난 결제에 부르면 PAYMENT_ALREADY_PROCESSED 로 막는다 — 확정된 결제를
            사람 손으로 뒤집을 수 있으면 안 된다.

            남긴 메모는 '[어드민 수동 종결]' 이 붙어 결제 이력에 기록된다. 나중에 이 건이
            왜 이 상태인지 자동 처리와 구분된다.
            """)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/api/v1/payments/{paymentNo}/resolve")
    public ApiResponse<PaymentDto.PaymentResponse> resolve(
            @PathVariable String paymentNo,
            @Valid @RequestBody PaymentDto.ResolveRequest req) {
        return ApiResponse.ok(paymentService.resolveStuckPayment(paymentNo, req.getOutcome(), req.getMemo()));
    }
}
