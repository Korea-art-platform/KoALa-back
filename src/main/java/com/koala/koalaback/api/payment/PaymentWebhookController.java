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

    /**
     * 나이스 결제통보(웹훅).
     *
     * <h3>결제창 복귀와 무엇이 다른가</h3>
     * <p>결제창 복귀는 <b>사용자의 브라우저</b>가 물고 온다. 그래서 사용자가 승인 도중 창을 닫거나
     * 우리 서버가 그 순간 떠 있지 않으면 결과가 우리에게 도달하지 않는다 — 돈은 빠져나갔는데
     * 주문은 미확정으로 남는다. 웹훅은 나이스 서버가 <b>우리 서버로 직접</b> 보내고, 실패하면
     * 다시 보낸다. 그 구멍을 메우는 것이 이 엔드포인트의 존재 이유다.
     *
     * <h3>"OK" 를 못 보내면 계속 다시 온다</h3>
     * <p>나이스는 응답 본문에 {@code OK} 문자열이 없으면 실패로 보고 재전송한다.
     * 본문 형식도 {@code text/html} 이어야 한다 — JSON 으로 돌려주면 실패로 친다.
     * 그래서 서명이 틀렸을 때만 거부하고, <b>처리 중 예외가 나도 200 "OK" 로 닫는다.</b>
     * 이미 기록은 남았고, 같은 전문을 무한히 다시 받아 봐야 같은 예외만 반복된다.
     *
     * <h3>서명 조합이 결제창과 다르다</h3>
     * <p>{@code hex(sha256(tid + amount + ediDate + secretKey))} — clientId 가 없다.
     * 결제창 쪽 규칙으로 검증하면 정상 요청이 전부 튕긴다.
     */
    @PostMapping(value = "/nice", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> niceWebhook(@RequestBody(required = false) String payload) {
        // required=true 면 본문이 빈 요청을 스프링이 먼저 400 으로 끊어, 이 메서드까지 오지 않는다.
        // 그러면 주소 확인 호출에 200 을 돌려줄 방법이 없어 웹훅 등록이 거부된다.
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
                // 빈 본문. 알릴 내용이 없으니 주소 확인용 호출로 본다
                log.info("[Webhook/Payments/Nice] 빈 본문 — 주소 확인으로 보고 넘긴다");
                return ResponseEntity.ok("OK");
            }
            tid = root.path("tid").asText(null);
            // 해시 입력은 전문에 적힌 문자열 그대로여야 한다. 숫자로 읽었다가 되돌리면
            // 1000 이 1000.0 이 되는 식으로 달라져 서명이 어긋난다
            amount = root.path("amount").isMissingNode() ? null : root.path("amount").asText();
            ediDate = root.path("ediDate").asText(null);
            signature = root.path("signature").asText(null);
        } catch (Exception e) {
            // 읽을 수 없는 본문으로는 어떤 결제도 확정할 수 없다. 다시 보내 봐야 결과가 같으므로
            // 거부해서 재전송을 부르지 않고 기록만 남긴다
            log.warn("[Webhook/Payments/Nice] 전문 파싱 실패 — 처리하지 않는다: {}", e.getMessage());
            return ResponseEntity.ok("OK");
        }

        if (!niceSignatureVerifier.verifyWebhook(tid, amount, ediDate, signature)) {
            if (tid == null || tid.isBlank()) {
                // 거래키가 없으면 알릴 결제 자체가 없다는 뜻이다 — 주소 확인용 호출로 본다.
                // 나이스는 웹훅을 등록할 때 이 주소를 먼저 찔러 보고 200 이 아니면 등록을 거부한다.
                log.info("[Webhook/Payments/Nice] 거래 정보 없는 호출 — 주소 확인으로 보고 넘긴다");
            } else {
                // 거래키는 있는데 서명이 안 맞는다. 위조이거나 시크릿 키가 틀렸다.
                // 시크릿이 틀린 경우라면 재전송을 받아도 계속 실패하므로, 다시 보내게 하는 대신
                // 사람을 부른다. 그동안 결제가 미확정으로 남는 것은 기존 미확정 알림이 잡는다.
                log.error("★[Webhook/Payments/Nice] 서명 검증 실패★ 처리하지 않는다: tid={}", tid);
                adminAlertNotifier.notifyServerError(
                        "NiceWebhookSignatureMismatch",
                        "나이스 웹훅 서명 불일치 (tid=" + tid + ") — 시크릿 키 설정을 확인하세요",
                        "POST", "/webhook/payments/nice", 0);
            }
            // 어느 쪽이든 200 "OK" 로 닫는다. 처리를 안 했을 뿐 거부는 하지 않는다.
            // 나이스는 200 "OK" 가 아니면 실패로 보고 계속 다시 보낸다.
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
