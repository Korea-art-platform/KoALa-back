package com.koala.koalaback.domain.order.event;

import com.koala.koalaback.domain.order.service.OrderService;
import com.koala.koalaback.domain.user.event.UserSignedUpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * 가입하면 같은 이메일로 했던 비회원 주문을 계정에 붙인다.
 *
 * 가입이 실제로 끝난 뒤에 움직인다. 같은 트랜잭션에서 하면 붙이다 실패했을 때
 * 가입까지 함께 되돌아간다 — 주문을 못 붙인 것 때문에 가입이 막히면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestOrderLinker {
    private final OrderService orderService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignedUp(UserSignedUpEvent event) {
        try {
            orderService.linkGuestOrders(event.userId(), event.email());
        } catch (Exception e) {
            // 붙이지 못해도 가입은 이미 끝났다. 주문은 비회원 조회로 찾을 수 있다.
            log.warn("비회원 주문 연결 실패 — userId={}: {}", event.userId(), e.getMessage());
        }
    }
}
