package com.koala.koalaback.infra.slack;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * 주문 외의 운영 알림 — 반품 신청, 결제 미확정, 수동 환불, 재고 소진, 서버 기동.
 *
 * <p>새 주문 알림은 {@link AdminOrderNotifier} 가 따로 맡는다. 주문은 결제 응답 경로에서
 * 호출되어 지연에 민감하고 메시지 형식도 다르다.
 *
 * <h3>여기서 예외가 나가면 안 된다</h3>
 * <p>호출 지점이 전부 <b>이미 되돌릴 수 없는 자리</b>다 — 결제는 승인됐고, 반품은 저장됐고,
 * 재고는 차감됐다. 알림이 실패했다고 그 흐름을 깨뜨리면 알림이 없는 것보다 나쁘다.
 * 그래서 모든 공개 메서드가 예외를 삼킨다.
 *
 * <h3>슬랙이 꺼져 있으면 조용히 통과한다</h3>
 * <p>{@code koala.slack.enabled=false} 면 {@link SlackNotifier} 빈 자체가 없다.
 * 로컬·테스트에서 설정 없이 돌아가야 하므로 {@link ObjectProvider} 로 선택 주입한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAlertNotifier {

    private final ObjectProvider<SlackNotifier> slackProvider;

    /** 반품·교환 신청이 들어왔다 — 어드민을 열어 봐야만 알 수 있던 것 */
    public void notifyReturnRequested(String returnNo, String orderNo, String returnType,
                                      String reason, String ordererName) {
        safeSend(() -> buildReturnRequested(returnNo, orderNo, returnType, reason, ordererName),
                "반품 신청 알림", returnNo);
    }

    /**
     * 결제가 미확정(IN_DOUBT)으로 남았다.
     *
     * <p>승인됐는지 아닌지 서버가 모르는 상태다. 고객 돈이 빠져나갔을 수도 있어
     * 사람이 PG 콘솔에서 확인해야 한다. 지금까지는 서버 로그에만 남았다.
     */
    public void notifyPaymentInDoubt(String orderNo, BigDecimal amount, String cause) {
        safeSend(() -> buildPaymentInDoubt(orderNo, amount, cause), "결제 미확정 알림", orderNo);
    }

    /**
     * 환불(취소) 요청을 보냈는데 처리 여부를 알 수 없다.
     *
     * <p>재시도하면 이중 환불이 날 수 있어 서버가 잠가 둔 상태다. 자동으로 풀리지 않으므로
     * 사람이 PG 콘솔에서 실제 취소 여부를 확인해야 한다.
     */
    public void notifyCancelInDoubt(String paymentNo, BigDecimal amount, String cause) {
        safeSend(() -> buildCancelInDoubt(paymentNo, amount, cause), "환불 미확정 알림", paymentNo);
    }

    /**
     * 보상 취소까지 실패해 고객 결제금이 남아 있을 수 있다.
     *
     * <p>운영 알림 중 가장 급한 건이다 — 돈이 실제로 붕 떠 있고, 자동으로 풀리지 않는다.
     */
    public void notifyManualRefundNeeded(String orderNo, BigDecimal amount,
                                         String pgTransactionId, String failureMessage) {
        safeSend(() -> buildManualRefundNeeded(orderNo, amount, pgTransactionId, failureMessage),
                "수동 환불 알림", orderNo);
    }

    /** 재고가 0이 되어 판매 중지로 전환됐다 */
    public void notifyStockDepleted(String skuCode, String skuName, String artistName) {
        safeSend(() -> buildStockDepleted(skuCode, skuName, artistName), "재고 소진 알림", skuCode);
    }

    /**
     * 처리되지 않은 500 오류가 났다.
     *
     * @param suppressed 쿨다운 동안 같은 오류로 묻힌 건수 — 0 이면 표시하지 않는다
     */
    public void notifyServerError(String exceptionName, String message,
                                  String method, String uri, int suppressed) {
        safeSend(() -> buildServerError(exceptionName, message, method, uri, suppressed),
                "500 알림", uri);
    }

    /**
     * 서버가 기동했다.
     *
     * <p>평소에는 배포 때만 올라온다. <b>배포하지 않았는데 올라오면 그 자체가 신호다</b> —
     * 죽었다가 살아났거나 롤백됐다는 뜻이다.
     */
    public void notifyApplicationStarted(String profile, String version) {
        safeSend(() -> buildApplicationStarted(profile, version), "기동 알림", version);
    }

    // ---------------------------------------------------------------- 메시지

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

        // 억제된 건수는 장애 규모를 말해 준다. 조용한 것과 묻힌 것은 다르다
        if (suppressed > 0) {
            sb.append("\n_같은 오류 ").append(suppressed).append("건이 함께 묻혔습니다._");
        }
        return sb.toString();
    }

    String buildApplicationStarted(String profile, String version) {
        return "🟢 *서버 기동* `" + nullSafe(profile) + "` · " + nullSafe(version) + "\n"
                + "_배포한 적이 없다면 재시작 또는 롤백입니다._";
    }

    // ---------------------------------------------------------------- 내부

    /**
     * 메시지 조립부터 발송까지 통째로 감싼다.
     *
     * <p>{@link java.util.function.Supplier} 로 받는 이유는 <b>메시지를 만드는 과정에서 나는
     * 예외까지</b> 삼키기 위해서다. 문자열을 인자로 받으면 조립은 호출부에서 일어나 여기서
     * 막지 못한다.
     */
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
