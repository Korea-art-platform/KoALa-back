package com.koala.koalaback.api.payment;

import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.security.TossWebhookVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhook/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final PaymentService paymentService;
    private final TossWebhookVerifier tossWebhookVerifier;

    @PostMapping("/toss")
    public ResponseEntity<Void> tossWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody String payload) {
        if (!tossWebhookVerifier.verify(authorization)) {
            log.warn("[Webhook/Payments/Toss] 서명 검증 실패 — 요청 거부");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            paymentService.handleWebhook("TOSS", payload);
            log.info("[Webhook/Payments/Toss] 처리 완료");
        } catch (Exception e) {
            log.error("[Webhook/Payments/Toss] 처리 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.ok().build();
    }
}
