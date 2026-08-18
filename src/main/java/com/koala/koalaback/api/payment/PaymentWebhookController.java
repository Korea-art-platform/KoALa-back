package com.koala.koalaback.api.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.security.NiceSignatureVerifier;
import com.koala.koalaback.global.security.TossWebhookVerifier;
import com.koala.koalaback.infra.slack.AdminAlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhook/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final PaymentService paymentService;
    private final TossWebhookVerifier tossWebhookVerifier;
    private final NiceSignatureVerifier niceSignatureVerifier;
    private final ObjectMapper objectMapper;
    private final AdminAlertNotifier adminAlertNotifier;

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

    @PostMapping(value = "/nice", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> niceWebhook(@RequestBody(required = false) String payload) {
        if (payload == null || payload.isBlank()) {
            log.info("[Webhook/Payments/Nice] 빈 본문 — 주소 확인으로 보고 넘긴다");
            return ResponseEntity.ok("OK");
        }

        String tid;
        String amount;
        String ediDate;
        String signature;
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.isMissingNode()) {
                log.info("[Webhook/Payments/Nice] 빈 본문 — 주소 확인으로 보고 넘긴다");
                return ResponseEntity.ok("OK");
            }
            tid = root.path("tid").asText(null);

            amount = root.path("amount").isMissingNode() ? null : root.path("amount").asText();
            ediDate = root.path("ediDate").asText(null);
            signature = root.path("signature").asText(null);
        } catch (Exception e) {
            log.warn("[Webhook/Payments/Nice] 전문 파싱 실패 — 처리하지 않는다: {}", e.getMessage());
            return ResponseEntity.ok("OK");
        }

        if (!niceSignatureVerifier.verifyWebhook(tid, amount, ediDate, signature)) {
            if (tid == null || tid.isBlank()) {
                log.info("[Webhook/Payments/Nice] 거래 정보 없는 호출 — 주소 확인으로 보고 넘긴다");
            } else {
                log.error("★[Webhook/Payments/Nice] 서명 검증 실패★ 처리하지 않는다: tid={}", tid);
                adminAlertNotifier.notifyServerError(
                        "NiceWebhookSignatureMismatch",
                        "나이스 웹훅 서명 불일치 (tid=" + tid + ") — 시크릿 키 설정을 확인하세요",
                        "POST", "/webhook/payments/nice", 0);
            }

            return ResponseEntity.ok("OK");
        }

        try {
            paymentService.handleWebhook("NICEPAY", payload);
            log.info("[Webhook/Payments/Nice] 처리 완료: tid={}", tid);
        } catch (Exception e) {
            log.error("[Webhook/Payments/Nice] 처리 오류: tid={}", tid, e);
        }

        return ResponseEntity.ok("OK");
    }
}
