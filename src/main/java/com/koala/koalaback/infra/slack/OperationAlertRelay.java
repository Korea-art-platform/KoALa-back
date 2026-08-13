package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.returnrequest.event.ReturnRequestedEvent;
import com.koala.koalaback.domain.sku.event.StockDepletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 운영 알림 중 <b>DB 트랜잭션 안에서 발생한 것</b>을 커밋 이후로 미뤄 내보내는 릴레이.
 *
 * <p>{@code AFTER_COMMIT} 인 이유는 {@link com.koala.koalaback.domain.order.event.OrderEventRelay}
 * 와 같다 — 트랜잭션 안에서 바로 보내면 그 뒤 롤백이 났을 때 DB 에 없는 일에 대한 알림이
 * 이미 나가 있다. 반품이 저장되지 않았는데 "반품 신청이 들어왔다"고 알리는 식이다.
 *
 * <p>결제 미확정·수동 환불은 여기를 거치지 않는다. 그쪽은 트랜잭션 <b>밖</b>의 보상 경로에서
 * 일어나므로 {@link AdminAlertNotifier} 를 직접 부른다.
 */
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
