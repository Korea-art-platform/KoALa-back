package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.cart.dto.CartDto;
import com.koala.koalaback.domain.cart.service.CartService;
import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.repository.SkuRepository;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 주문 취소.
 *
 * <p>취소는 재고를 되돌리려고 {@code SELECT ... FOR UPDATE} 로 잠금을 건다. 그런데
 * {@code OrderService} 에는 클래스 단위로 {@code readOnly = true} 가 걸려 있어서, 취소
 * 메서드에 트랜잭션 표시가 없으면 <b>읽기 전용 트랜잭션 안에서</b> 그 잠금을 시도하게 된다.
 * 안쪽 메서드에 {@code @Transactional} 이 붙어 있어도 소용없다 — 새로 열지 않고 합류하기 때문이다.
 * MySQL 은 이를 거부하고, 취소는 500 으로 실패한다.
 *
 * <p>단위 테스트로는 잡히지 않는다. 서비스를 직접 만들어 부르면 트랜잭션 자체가 없기 때문이다.
 * 실제 DB 와 스프링 프록시를 함께 태워야 드러난다.
 */
@DisplayName("주문 취소")
class OrderCancelIntegrationTest extends IntegrationTestSupport {
    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private StockService stockService;
    @Autowired private SkuRepository skuRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_shipments");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM cart_items");
        jdbcTemplate.update("DELETE FROM carts");
        jdbcTemplate.update("DELETE FROM sku_stock_ledger");
        jdbcTemplate.update("DELETE FROM skus");
        jdbcTemplate.update("DELETE FROM artists");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("결제 전 주문을 취소하면 재고가 돌아오고 주문이 닫힌다")
    void cancelOrder_restoresStockAndClosesOrder() {
        Long userId = givenUser();
        Sku sku = givenSkuWithStock(5);
        String orderNo = givenOrder(userId, sku, 2);

        assertThat(stockOf(sku.getId())).as("주문으로 2개 차감된 상태").isEqualTo(3);

        OrderDto.OrderDetailResponse response = orderService.cancelOrder(userId, orderNo);

        assertThat(response.getOrderStatus()).isEqualTo("CANCELLED");
        assertThat(stockOf(sku.getId())).as("취소하면 재고가 돌아온다").isEqualTo(5);
    }

    @Test
    @DisplayName("어드민 강제 취소도 같은 경로를 탄다")
    void adminCancelOrder_restoresStock() {
        Long userId = givenUser();
        Sku sku = givenSkuWithStock(5);
        String orderNo = givenOrder(userId, sku, 2);

        orderService.adminCancelOrder(orderNo, adminCancelRequest());

        assertThat(stockOf(sku.getId())).as("취소하면 재고가 돌아온다").isEqualTo(5);
    }

    private OrderDto.AdminCancelRequest adminCancelRequest() {
        OrderDto.AdminCancelRequest req = mock(OrderDto.AdminCancelRequest.class);
        given(req.getReason()).willReturn("테스트 강제취소");
        return req;
    }

    private String givenOrder(Long userId, Sku sku, int quantity) {
        addToCart(userId, sku, quantity);
        return orderService.createOrder(userId, createRequest()).getOrderNo();
    }

    private Long givenUser() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .userCode("CTEST-" + uid)
                .email("canceller-" + uid + "@koala.test")
                .passwordHash("{noop}test")
                .name("취소 테스터")
                .phone("01000000000")
                .build());
        return user.getId();
    }

    private Sku givenSkuWithStock(int stock) {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Artist artist = artistRepository.save(Artist.builder()
                .artistCode("CTEST-" + uid)
                .name("테스트 작가")
                .slug("ctest-artist-" + uid)
                .build());

        Sku sku = Sku.builder()
                .skuCode("CTEST-" + uid)
                .artist(artist)
                .name("취소용 작품")
                .slug("ctest-sku-" + uid)
                .skuType("ARTWORK")
                .mainCategory(Sku.MAIN_NORMAL)
                .genre("ART_TOY")
                .currency("KRW")
                .listPrice(BigDecimal.valueOf(20_000))
                .build();
        sku.publish();
        skuRepository.save(sku);

        stockService.initialize(sku, stock, "취소 테스트 초기 재고");
        return sku;
    }

    private void addToCart(Long userId, Sku sku, int quantity) {
        CartDto.AddItemRequest req = mock(CartDto.AddItemRequest.class);
        given(req.getSkuCode()).willReturn(sku.getSkuCode());
        given(req.getQuantity()).willReturn(quantity);
        cartService.addItem(userId, req);
    }

    private OrderDto.CreateRequest createRequest() {
        OrderDto.ShipmentRequest shipment = mock(OrderDto.ShipmentRequest.class);
        given(shipment.getRecipientName()).willReturn("수령인");
        given(shipment.getRecipientPhone()).willReturn("01011112222");
        given(shipment.getZipCode()).willReturn("06236");
        given(shipment.getAddress1()).willReturn("서울시 강남구");
        given(shipment.getAddress2()).willReturn("101동 101호");

        OrderDto.CreateRequest req = mock(OrderDto.CreateRequest.class);
        given(req.getOrdererName()).willReturn("주문자");
        given(req.getOrdererEmail()).willReturn("orderer@koala.test");
        given(req.getOrdererPhone()).willReturn("01033334444");
        given(req.getShipment()).willReturn(shipment);
        given(req.getCartItemIds()).willReturn(List.of());
        return req;
    }

    private int stockOf(Long skuId) {
        Integer sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(delta), 0) FROM sku_stock_ledger WHERE sku_id = ?",
                Integer.class, skuId);
        return sum == null ? 0 : sum;
    }
}
