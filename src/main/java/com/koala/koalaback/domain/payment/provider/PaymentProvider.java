package com.koala.koalaback.domain.payment.provider;

import java.math.BigDecimal;

public interface PaymentProvider {

    String getProviderCode();

    PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount);

    PaymentCancelResult cancel(String pgTransactionId, BigDecimal cancelAmount, String reason);

    /**
     * 주문번호로 PG 측 결제 상태를 재조회한다.
     *
     * <p>승인 요청을 보냈는데 응답을 못 받은 경우(타임아웃 등) 실제로 승인됐는지 확인하는 용도.
     * 이 경로가 없으면 "돈은 빠져나갔는데 주문은 미결제" 상태를 스스로 해소할 수 없다.
     */
    PaymentLookupResult lookup(String orderId);

    /**
     * PG 호출 결과 구분.
     *
     * <p>{@code success} 불리언 하나로는 "거절당함"과 "결과를 모름"을 구별할 수 없다.
     * 이 둘은 후속 처리가 정반대다 — 거절은 실패 확정이지만,
     * 미확정은 승인됐을 수도 있으므로 절대 실패로 단정하면 안 된다.
     */
    enum Outcome {
        /** PG 가 처리를 확정했다 */
        SUCCEEDED,
        /** PG 가 명시적으로 거절했다(4xx) — 승인되지 않은 것이 확실하다 */
        REJECTED,
        /** 타임아웃·연결 실패·5xx — 처리됐는지 알 수 없다 */
        UNKNOWN
    }

    record PaymentConfirmResult(
            Outcome outcome,
            String pgTransactionId,
            String approvalNo,
            BigDecimal approvedAmount,
            String rawResponse,
            String failureCode,
            String failureMessage
    ) {
        public boolean isApproved() { return outcome == Outcome.SUCCEEDED; }
        public boolean isUnknown()  { return outcome == Outcome.UNKNOWN; }

        public static PaymentConfirmResult approved(String pgTransactionId, String approvalNo,
                                                    BigDecimal approvedAmount, String rawResponse) {
            return new PaymentConfirmResult(Outcome.SUCCEEDED, pgTransactionId, approvalNo,
                    approvedAmount, rawResponse, null, null);
        }

        public static PaymentConfirmResult rejected(String failureCode, String failureMessage) {
            return new PaymentConfirmResult(Outcome.REJECTED, null, null, null, null,
                    failureCode, failureMessage);
        }

        public static PaymentConfirmResult unknown(String failureCode, String failureMessage) {
            return new PaymentConfirmResult(Outcome.UNKNOWN, null, null, null, null,
                    failureCode, failureMessage);
        }
    }

    record PaymentCancelResult(
            Outcome outcome,
            BigDecimal cancelledAmount,
            String rawResponse,
            String failureCode,
            String failureMessage
    ) {
        public boolean isCancelled() { return outcome == Outcome.SUCCEEDED; }
        public boolean isUnknown()   { return outcome == Outcome.UNKNOWN; }

        public static PaymentCancelResult cancelled(BigDecimal cancelledAmount, String rawResponse) {
            return new PaymentCancelResult(Outcome.SUCCEEDED, cancelledAmount, rawResponse, null, null);
        }

        public static PaymentCancelResult rejected(String failureCode, String failureMessage) {
            return new PaymentCancelResult(Outcome.REJECTED, null, null, failureCode, failureMessage);
        }

        public static PaymentCancelResult unknown(String failureCode, String failureMessage) {
            return new PaymentCancelResult(Outcome.UNKNOWN, null, null, failureCode, failureMessage);
        }
    }

    /**
     * 재조회 결과.
     *
     * @param queried  재조회 자체가 성공했는지(false 면 PG 상태를 여전히 모른다)
     * @param found    PG 에 해당 주문의 결제가 존재하는지
     * @param approved 승인 완료 상태인지
     */
    record PaymentLookupResult(
            boolean queried,
            boolean found,
            boolean approved,
            String pgTransactionId,
            String approvalNo,
            BigDecimal approvedAmount,
            String rawResponse
    ) {
        /** 재조회에 성공했고 승인되지 않은 것이 확인됨 — 실패로 확정해도 안전하다 */
        public boolean isDefinitelyNotApproved() { return queried && !approved; }

        public static PaymentLookupResult unavailable() {
            return new PaymentLookupResult(false, false, false, null, null, null, null);
        }
    }
}
