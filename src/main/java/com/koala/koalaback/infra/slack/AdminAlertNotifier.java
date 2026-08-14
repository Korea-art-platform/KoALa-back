package com.koala.koalaback.infra.slack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAlertNotifier {
    private final ObjectProvider<SlackNotifier> slackProvider;

    public void notifyReturnRequested(String returnNo, String orderNo, String returnType,
                                      String reason, String ordererName) {
        safeSend(() -> buildReturnRequested(returnNo, orderNo, returnType, reason, ordererName),
                "반품 신청 알림", returnNo);
    }

    public void notifyPaymentInDoubt(String orderNo, BigDecimal amount, String cause) {
        safeSend(() -> buildPaymentInDoubt(orderNo, amount, cause), "결제 미확정 알림", orderNo);
    }

    public void notifyCancelInDoubt(String paymentNo, BigDecimal amount, String cause) {
        safeSend(() -> buildCancelInDoubt(paymentNo, amount, cause), "환불 미확정 알림", paymentNo);
    }

    public void notifyManualRefundNeeded(String orderNo, BigDecimal amount,
                                         String pgTransactionId, String failureMessage) {
        safeSend(() -> buildManualRefundNeeded(orderNo, amount, pgTransactionId, failureMessage),
                "수동 환불 알림", orderNo);
    }

    public void notifyStockDepleted(String skuCode, String skuName, String artistName) {
        safeSend(() -> buildStockDepleted(skuCode, skuName, artistName), "재고 소진 알림", skuCode);
    }

    public void notifyServerError(String exceptionName, String message,
                                  String method, String uri, int suppressed) {
        safeSend(() -> buildServerError(exceptionName, message, method, uri, suppressed),
                "500 알림", uri);
    }

    public void notifyApplicationStarted(String profile, String version) {
        safeSend(() -> buildApplicationStarted(profile, version), "기동 알림", version);
    }

    String buildReturnRequested(String returnNo, String orderNo, String returnType,
                                String reason, String ordererName) {
        return "🔁 *" + typeLabel(returnType) + " 신청*\n"
                + "접수번호 `" + returnNo + "`  ·  주문 `" + orderNo + "`\n"
                + "신청자 " + nullSafe(ordererName) + "\n"
                + "사유 " + nullSafe(reason);
    }

    String buildPaymentInDoubt(String orderNo, BigDecimal amount, String cause) {
        return "🚨 *결제 미확정* " + money(amount) + "\n"
                + "주문 `" + nullSafe(orderNo) + "`\n"
                + "원인 " + nullSafe(cause) + "\n"
                + "_승인 여부를 서버가 모릅니다. PG 콘솔에서 확인이 필요합니다._";
    }

    String buildCancelInDoubt(String paymentNo, BigDecimal amount, String cause) {
        return "🚨 *환불 미확정* " + money(amount) + "\n"
                + "결제번호 `" + nullSafe(paymentNo) + "`\n"
                + "원인 " + nullSafe(cause) + "\n"
                + "_재시도하면 이중 환불이 날 수 있어 잠가 뒀습니다. PG 콘솔 확인이 필요합니다._";
    }

    String buildManualRefundNeeded(String orderNo, BigDecimal amount,
                                   String pgTransactionId, String failureMessage) {
        return "🔴 *수동 환불 필요* " + money(amount) + "\n"
                + "주문 `" + nullSafe(orderNo) + "`\n"
                + "거래번호 `" + nullSafe(pgTransactionId) + "`\n"
                + "실패 사유 " + nullSafe(failureMessage) + "\n"
                + "_보상 취소가 실패했습니다. 고객 결제금이 남아 있을 수 있습니다._";
    }

    String buildStockDepleted(String skuCode, String skuName, String artistName) {
        StringBuilder sb = new StringBuilder("📦 *재고 소진* — 판매 중지로 전환\n");
        if (artistName != null && !artistName.isBlank()) {
            sb.append('[').append(artistName).append("] ");
        }
        return sb.append(nullSafe(skuName))
                .append("  `").append(nullSafe(skuCode)).append('`')
                .toString();
    }

    String buildServerError(String exceptionName, String message,
                            String method, String uri, int suppressed) {
        StringBuilder sb = new StringBuilder("⚠️ *서버 오류 500*\n");
        sb.append('`').append(nullSafe(method)).append(' ').append(nullSafe(uri)).append("`\n");
        sb.append(nullSafe(exceptionName)).append(" — ").append(nullSafe(message));

        if (suppressed > 0) {
            sb.append("\n_같은 오류 ").append(suppressed).append("건이 함께 묻혔습니다._");
        }
        return sb.toString();
    }

    String buildApplicationStarted(String profile, String version) {
        return "🟢 *서버 기동* `" + nullSafe(profile) + "` · " + nullSafe(version) + "\n"
                + "_배포한 적이 없다면 재시작 또는 롤백입니다._";
    }

    private void safeSend(java.util.function.Supplier<String> messageSupplier,
                          String what, String key) {
        try {
            SlackNotifier slack = slackProvider.getIfAvailable();
            if (slack == null) return;

            slack.send(messageSupplier.get());
        } catch (Exception e) {
            log.warn("{} 실패 (업무 처리는 정상): key={}, error={}", what, key, e.getMessage());
        }
    }

    private String money(BigDecimal amount) {
        if (amount == null) return "-";
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    private String typeLabel(String returnType) {
        if ("EXCHANGE".equals(returnType)) return "교환";
        if ("RETURN".equals(returnType)) return "반품";
        return nullSafe(returnType);
    }

    private String nullSafe(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
}
