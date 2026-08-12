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
import com.koala.koalaback.global.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 주문 생성 통합 테스트.
 *
 * <p>재고 차감·주문 저장·장바구니 비우기가 <b>한 트랜잭션</b>이라는 것이 핵심이라
 * 실제 DB 로 검증한다(Mockito 로는 롤백을 확인할 수 없다).
 * 테스트 클래스에 {@code @Transactional} 을 붙이지 않는다 — 붙이면 테스트 트랜잭션이
 * 전부를 감싸버려 "서비스의 트랜잭션이 롤백됐는지"를 확인할 수 없다.
 */
@DisplayName("주문 생성")
class OrderCreateIntegrationTest extends IntegrationTestSupport {

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
    @DisplayName("정상 주문 — 재고 차감·주문 저장·장바구니 비우기가 모두 반영된다")
    void createOrder_success_appliesStockOrderAndCart() {
        // given
        Long userId = givenUser();
        Sku sku = givenSkuWithStock("아트토이", 10, BigDecimal.valueOf(20_000));
        addToCart(userId, sku, 2);

        // when
        OrderDto.OrderDetailResponse response = orderService.createOrder(userId, createRequest());

        // then
        assertThat(response.getOrderNo()).isNotBlank();
        assertThat(stockOf(sku.getId())).as("재고 10개에서 2개 차감").isEqualTo(8);
        assertThat(countOrders()).as("주문이 저장됨").isEqualTo(1);
        assertThat(countOrderItems()).as("주문 아이템이 저장됨").isEqualTo(1);
        assertThat(cartService.getCart(userId).getItems())
                .as("주문한 아이템은 장바구니에서 빠짐").isEmpty();
    }

    @Test
    @DisplayName("재고 부족 — 예외가 나고 주문·장바구니가 전부 롤백된다")
    void createOrder_insufficientStock_rollsBackEverything() {
        // given — 장바구니에 3개를 담은 뒤 다른 주문이 재고를 가져가 1개만 남은 상황
        // (CartService.addItem 이 담는 시점에 재고를 검증하므로 담은 뒤에 재고를 줄인다)
        Long userId = givenUser();
        Sku sku = givenSkuWithStock("한정판", 10, BigDecimal.valueOf(50_000));
        addToCart(userId, sku, 3);
        drainStock(sku, 9);   // 10 → 1

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, createRequest()))
                .isInstanceOf(BusinessException.class);

        assertThat(stockOf(sku.getId())).as("재고는 그대로 1").isEqualTo(1);
        assertThat(countOrders()).as("주문이 남아있으면 안 됨").isZero();
        assertThat(countOrderItems()).isZero();
        assertThat(cartService.getCart(userId).getItems())
                .as("장바구니도 비워지면 안 됨").hasSize(1);
    }

    @Test
    @DisplayName("여러 아이템 중 하나만 재고 부족 — 이미 차감된 다른 아이템까지 전부 롤백된다")
    void createOrder_partialShortage_rollsBackAlreadyDeductedItems() {
        // given — 둘 다 담은 뒤 scarce 쪽 재고만 1개로 줄어든 상황
        Long userId = givenUser();
        Sku plenty = givenSkuWithStock("재고충분", 10, BigDecimal.valueOf(10_000));
        Sku scarce = givenSkuWithStock("재고부족", 10, BigDecimal.valueOf(30_000));
        addToCart(userId, plenty, 2);
        addToCart(userId, scarce, 5);
        drainStock(scarce, 9);   // 10 → 1

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userId, createRequest()))
                .isInstanceOf(BusinessException.class);

        // 락 순서상 plenty 가 먼저 차감된 뒤 scarce 에서 터지므로,
        // 롤백이 제대로 안 되면 plenty 재고만 줄어든 채 남는다.
        assertThat(stockOf(plenty.getId()))
                .as("먼저 차감된 아이템도 원복돼야 한다").isEqualTo(10);
        assertThat(stockOf(scarce.getId())).isEqualTo(1);
        assertThat(countOrders()).isZero();
        assertThat(cartService.getCart(userId).getItems()).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────

    private Long givenUser() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .userCode("UTEST-" + uid)
                .email("buyer-" + uid + "@koala.test")
                .passwordHash("{noop}test")
                .name("테스트 구매자")
                .phone("01000000000")
                .build());
        return user.getId();
    }

    private Sku givenSkuWithStock(String name, int stock, BigDecimal price) {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Artist artist = artistRepository.save(Artist.builder()
                .artistCode("OTEST-" + uid)
                .name("테스트 작가")
                .slug("otest-artist-" + uid)
                .build());

        Sku sku = Sku.builder()
                .skuCode("OTEST-" + uid)
                .artist(artist)
                .name(name)
                .slug("otest-sku-" + uid)
                .skuType("ARTWORK")
                .mainCategory(Sku.MAIN_NORMAL)
                .genre("ART_TOY")
                .currency("KRW")
                .listPrice(price)
                .build();
        sku.publish();   // createOrder 는 ACTIVE 상태만 허용
        skuRepository.save(sku);

        stockService.initialize(sku, stock, "주문 테스트 초기 재고");
        return sku;
    }

    /** 장바구니에 담은 뒤 다른 주문이 재고를 가져간 상황을 만든다 */
    private void drainStock(Sku sku, int quantity) {
        stockService.deduct(sku.getId(), quantity, "test_drain", null);
    }

    private void addToCart(Long userId, Sku sku, int quantity) {
        CartDto.AddItemRequest req = mock(CartDto.AddItemRequest.class);
        given(req.getSkuCode()).willReturn(sku.getSkuCode());
        given(req.getQuantity()).willReturn(quantity);
        cartService.addItem(userId, req);
    }

    /** DTO 에 생성자가 없어 목으로 만든다 — 값 전달만 하는 객체라 목으로 충분하다 */
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
        return sum != null ? sum : 0;
    }

    private int countOrders() {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        return c != null ? c : 0;
    }

    private int countOrderItems() {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class);
        return c != null ? c : 0;
    }
}
