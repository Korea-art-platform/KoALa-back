package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.order.repository.OrderShipmentRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("현장결제")
@TestPropertySource(properties = "pii.encryption.key=a29hbGEtdGVzdC1rZXktZm9yLWd1ZXN0LWxvb2t1cCE=")
class OnSitePaymentServiceTest extends IntegrationTestSupport {
    private static final String ARTIST_CODE = "ONSITE-TEST-ART";

    @Autowired private OnSitePaymentService onSitePaymentService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderShipmentRepository orderShipmentRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        artistRepository.save(Artist.builder()
                .artistCode(ARTIST_CODE)
                .name("현장작가")
                .slug("onsite-test-artist")
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE s FROM order_shipments s JOIN orders o ON o.id = s.order_id WHERE o.order_channel = 'ON_SITE'");
        jdbcTemplate.update("DELETE i FROM order_items i JOIN orders o ON o.id = i.order_id WHERE o.order_channel = 'ON_SITE'");
        jdbcTemplate.update("DELETE FROM orders WHERE order_channel = 'ON_SITE'");
        jdbcTemplate.update("DELETE FROM artists WHERE artist_code = ?", ARTIST_CODE);
    }

    private OnSitePaymentDto.CreateRequest request(long amount, boolean taxExempt) {
        OnSitePaymentDto.CreateRequest req = mock(OnSitePaymentDto.CreateRequest.class);
        when(req.getItemName()).thenReturn("  전시 DP 원작  ");
        when(req.getArtistCode()).thenReturn(ARTIST_CODE);
        when(req.getAmount()).thenReturn(amount);
        when(req.getTaxExempt()).thenReturn(taxExempt);
        return req;
    }

    @Test
    @DisplayName("상품 없이 품목명·금액으로 결제 대기 주문을 만들고, 과세 품목은 포함된 부가세를 1/11 로 뗀다")
    void createsPendingOrderWithoutSku() {
        OnSitePaymentDto.Response created = onSitePaymentService.create(request(330_000, false));

        assertThat(created.getPayToken()).hasSize(32);
        assertThat(created.getOrderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(created.getItemName()).isEqualTo("전시 DP 원작");

        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findByOrderNo(created.getOrderNo()).orElseThrow();
            assertThat(order.isOnSite()).isTrue();
            assertThat(order.isGuest()).isTrue();
            assertThat(order.getTotalAmount()).isEqualByComparingTo("330000");
            assertThat(order.getTaxAmount()).isEqualByComparingTo("30000");
            assertThat(order.getProductAmount()).isEqualByComparingTo("300000");
            assertThat(order.getShippingAmount()).isEqualByComparingTo(BigDecimal.ZERO);

            OrderItem item = order.getOrderItems().get(0);
            assertThat(item.getSku()).isNull();
            assertThat(item.getArtist().getArtistCode()).isEqualTo(ARTIST_CODE);
            assertThat(item.getLineTotalAmount()).isEqualByComparingTo("330000");
        });

        OnSitePaymentDto.PublicResponse info = onSitePaymentService.getByToken(created.getPayToken());
        assertThat(info.isPayable()).isTrue();
        assertThat(info.isPaid()).isFalse();
        assertThat(info.getAmount()).isEqualByComparingTo("330000");
    }

    @Test
    @DisplayName("면세 품목은 부가세 없이 전액이 공급가액이 된다")
    void taxExemptHasNoVat() {
        OnSitePaymentDto.Response created = onSitePaymentService.create(request(1_100_000, true));

        Order order = orderRepository.findByOrderNo(created.getOrderNo()).orElseThrow();
        assertThat(order.getTaxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getProductAmount()).isEqualByComparingTo("1100000");
    }

    @Test
    @DisplayName("결제가 확정되면 바로 배송 완료가 되어 작가 정산에 잡힌다")
    void paidOnSiteOrderIsDeliveredAtOnce() {
        OnSitePaymentDto.Response created = onSitePaymentService.create(request(50_000, false));

        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findByOrderNo(created.getOrderNo()).orElseThrow().markPaid());

        Order order = orderRepository.findByOrderNo(created.getOrderNo()).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo("DELIVERED");
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(orderShipmentRepository.findByOrderId(order.getId()).orElseThrow().getDeliveredAt()).isNotNull();

        OnSitePaymentDto.PublicResponse info = onSitePaymentService.getByToken(created.getPayToken());
        assertThat(info.isPaid()).isTrue();
        assertThat(info.isPayable()).isFalse();
    }

    @Test
    @DisplayName("없는 토큰은 주문을 찾을 수 없다고 답한다")
    void unknownTokenIsNotFound() {
        assertThatThrownBy(() -> onSitePaymentService.getByToken("0".repeat(32)))
                .isInstanceOf(BusinessException.class);
    }
}
