package com.koala.koalaback.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.global.security.NiceSignatureVerifier;
import com.koala.koalaback.global.security.TossWebhookVerifier;
import com.koala.koalaback.infra.slack.AdminAlertNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("나이스 웹훅 수신")
class NiceWebhookControllerTest {
    private static final String SECRET_KEY = "test_secret_value";

    private MockMvc mockMvc;
    private PaymentService paymentService;
    private AdminAlertNotifier adminAlertNotifier;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        adminAlertNotifier = mock(AdminAlertNotifier.class);

        PaymentWebhookController controller = new PaymentWebhookController(
                paymentService,
                mock(TossWebhookVerifier.class),
                new NiceSignatureVerifier("S2_test_client", SECRET_KEY),
                new ObjectMapper(),
                adminAlertNotifier);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String body(String tid, String amount, String ediDate, String signature) {
        return """
                {"tid":"%s","amount":%s,"ediDate":"%s","signature":"%s","status":"paid"}
                """.formatted(tid, amount, ediDate, signature);
    }

    private String sign(String tid, String amount, String ediDate) {
        return hex(sha256(tid + amount + ediDate + SECRET_KEY));
    }

    @Test
    @DisplayName("서명이 맞으면 처리한다")
    void validSignatureIsProcessed() throws Exception {
        String ediDate = "2026-08-18T14:00:00.000+0900";

        mockMvc.perform(post("/webhook/payments/nice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tid_1", "150000", ediDate, sign("tid_1", "150000", ediDate))))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentService).handleWebhook(eq("NICEPAY"), anyString());
    }

    @Test
    @DisplayName("거래 정보가 없으면 주소 확인 호출로 보고 200 OK — 이게 없으면 웹훅 등록이 거부된다")
    void probeCallIsAcceptedWithoutProcessing() throws Exception {
        mockMvc.perform(post("/webhook/payments/nice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentService, never()).handleWebhook(anyString(), anyString());
        verify(adminAlertNotifier, never())
                .notifyServerError(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("빈 본문도 200 OK 로 닫는다")
    void emptyBodyIsAccepted() throws Exception {
        mockMvc.perform(post("/webhook/payments/nice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentService, never()).handleWebhook(anyString(), anyString());
    }

    @Test
    @DisplayName("거래는 있는데 서명이 틀리면 처리하지 않고 사람을 부른다")
    void tamperedPayloadIsNotProcessedAndAlerts() throws Exception {
        String ediDate = "2026-08-18T14:00:00.000+0900";
        String signature = sign("tid_1", "150000", ediDate);

        mockMvc.perform(post("/webhook/payments/nice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tid_1", "1000", ediDate, signature)))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentService, never()).handleWebhook(anyString(), anyString());
        verify(adminAlertNotifier)
                .notifyServerError(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("처리 중 예외가 나도 200 OK 로 닫는다 — 같은 전문을 다시 받아도 같은 예외만 난다")
    void processingFailureStillReturnsOk() throws Exception {
        String ediDate = "2026-08-18T14:00:00.000+0900";
        doThrow(new RuntimeException("DB 연결 실패"))
                .when(paymentService).handleWebhook(anyString(), anyString());

        mockMvc.perform(post("/webhook/payments/nice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tid_1", "150000", ediDate, sign("tid_1", "150000", ediDate))))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("응답은 text/html 이어야 한다 — JSON 으로 돌려주면 나이스가 실패로 친다")
    void respondsAsTextHtml() throws Exception {
        mockMvc.perform(post("/webhook/payments/nice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
