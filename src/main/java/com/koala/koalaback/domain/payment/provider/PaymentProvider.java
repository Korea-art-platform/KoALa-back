package com.koala.koalaback.domain.payment.provider;

import java.math.BigDecimal;

public interface PaymentProvider {
    String getProviderCode();

    PaymentConfirmResult confirm(String paymentKey, String orderId, BigDecimal amount);

    PaymentCancelResult cancel(String pgTransactionId, BigDecimal cancelAmount, String reason);

    PaymentLookupResult lookup(String orderId);

    enum Outcome {
        SUCCEEDED,

        REJECTED,

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

    record PaymentLookupResult(
            boolean queried,
            boolean found,
            boolean approved,
            String pgTransactionId,
            String approvalNo,
            BigDecimal approvedAmount,
            String rawResponse
    ) {
        public boolean isDefinitelyNotApproved() { return queried && !approved; }

        public static PaymentLookupResult unavailable() {
            return new PaymentLookupResult(false, false, false, null, null, null, null);
        }
    }
}
