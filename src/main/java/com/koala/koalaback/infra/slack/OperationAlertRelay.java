package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.returnrequest.event.ReturnRequestedEvent;
import com.koala.koalaback.domain.sku.event.StockDepletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OperationAlertRelay {
    private final AdminAlertNotifier adminAlertNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReturnRequested(ReturnRequestedEvent event) {
        adminAlertNotifier.notifyReturnRequested(
                event.returnNo(), event.orderNo(), event.returnType(),
                event.reason(), event.ordererName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockDepleted(StockDepletedEvent event) {
        adminAlertNotifier.notifyStockDepleted(
                event.skuCode(), event.skuName(), event.artistName());
    }
}
