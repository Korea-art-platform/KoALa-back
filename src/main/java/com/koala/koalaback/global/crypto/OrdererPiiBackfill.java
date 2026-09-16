package com.koala.koalaback.global.crypto;

import com.koala.koalaback.domain.order.entity.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrdererPiiBackfill implements ApplicationRunner {
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES = 100;

    private static final String PENDING_IDS =
            "SELECT id FROM orders"
            + " WHERE orderer_email_hash IS NULL OR orderer_email NOT LIKE 'enc:%'"
            + " ORDER BY id LIMIT " + BATCH_SIZE;

    private static final String PENDING_COUNT =
            "SELECT COUNT(*) FROM orders"
            + " WHERE orderer_email_hash IS NULL OR orderer_email NOT LIKE 'enc:%'";

    private static final String REWRITE = """
            UPDATE Order o
               SET o.ordererName = :name,
                   o.ordererEmail = :email,
                   o.ordererPhone = :phone,
                   o.ordererEmailHash = :emailHash,
                   o.ordererPhoneHash = :phoneHash,
                   o.ordererPhoneLast4 = :last4
             WHERE o.id = :id
            """;

    @PersistenceContext
    private EntityManager em;

    private final PiiIndex piiIndex;
    private final PlatformTransactionManager transactionManager;

    @Value("${pii.backfill.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[PII] 주문자 정보 채우기 건너뜀 — pii.backfill.enabled=false");
            return;
        }
        if (!piiIndex.isEnabled()) {
            log.warn("[PII] 암호화 키가 없어 주문자 정보 채우기를 건너뜁니다");
            return;
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int done = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> ids = tx.execute(status -> em.createNativeQuery(PENDING_IDS, Long.class)
                    .getResultList());

            if (ids == null || ids.isEmpty()) break;

            tx.executeWithoutResult(status -> {
                for (Long id : ids) {
                    Order order = em.find(Order.class, id);
                    if (order == null) continue;

                    em.createQuery(REWRITE)
                            .setParameter("name", order.getOrdererName())
                            .setParameter("email", order.getOrdererEmail())
                            .setParameter("phone", order.getOrdererPhone())
                            .setParameter("emailHash", piiIndex.ofEmail(order.getOrdererEmail()))
                            .setParameter("phoneHash", piiIndex.ofPhone(order.getOrdererPhone()))
                            .setParameter("last4", piiIndex.last4Of(order.getOrdererPhone()))
                            .setParameter("id", id)
                            .executeUpdate();
                }
                em.clear();
            });

            done += ids.size();
        }

        Number remaining = (Number) tx.execute(status ->
                em.createNativeQuery(PENDING_COUNT).getSingleResult());

        if (done > 0) {
            log.info("[PII] 주문자 정보 채우기 완료 — 처리 {}건, 남은 {}건", done, remaining);
        }
        if (remaining != null && remaining.longValue() > 0) {
            log.warn("[PII] 아직 남은 주문이 있습니다 — 다음 기동에서 이어서 처리합니다: {}건", remaining);
        }
    }
}
