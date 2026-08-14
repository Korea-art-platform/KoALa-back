package com.koala.koalaback.domain.settlement.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.repository.ArtistRepository;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.order.repository.OrderShipmentRepository;
import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import com.koala.koalaback.domain.returnrequest.repository.ReturnRequestRepository;
import com.koala.koalaback.domain.settlement.dto.SettlementDto;
import com.koala.koalaback.domain.settlement.repository.ArtistSettlementRepository;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("작가 정산")
class SettlementServiceTest extends IntegrationTestSupport {
    @Autowired private SettlementService settlementService;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderShipmentRepository orderShipmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReturnRequestRepository returnRequestRepository;
    @Autowired private ArtistSettlementRepository settlementRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final YearMonth LAST_MONTH = YearMonth.now().minusMonths(1);
    private static final String PERIOD = LAST_MONTH.toString();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM artist_settlements");
        jdbcTemplate.update("DELETE FROM return_requests");
        jdbcTemplate.update("DELETE FROM order_shipments");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM artists");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("배송완료된 주문만 정산에 들어간다")
    void onlyDeliveredCounts() {
        Artist artist = givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artist, "100000", LAST_MONTH);
        givenShippedOrder(artist, "500000", LAST_MONTH);

        SettlementDto.PeriodSummaryResponse result = settlementService.getPeriod(PERIOD);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).grossAmount()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("다른 달에 배송완료된 건은 들어오지 않는다")
    void otherMonthExcluded() {
        Artist artist = givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artist, "100000", LAST_MONTH);
        givenDeliveredOrder(artist, "999000", LAST_MONTH.minusMonths(1));

        assertThat(settlementService.getPeriod(PERIOD).totalGross())
                .isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("작가가 여럿이면 각자 자기 몫만 받는다")
    void perArtistSeparation() {
        Artist a = givenArtist("김작가", "0.2000");
        Artist b = givenArtist("이작가", "0.2000");
        givenDeliveredOrder(a, "100000", LAST_MONTH);
        givenDeliveredOrder(b, "300000", LAST_MONTH);

        SettlementDto.PeriodSummaryResponse result = settlementService.getPeriod(PERIOD);

        assertThat(result.items()).hasSize(2);
        assertThat(result.totalGross()).isEqualByComparingTo("400000");
    }

    @Test
    @DisplayName("수수료 20% — 지급액은 순매출에서 수수료를 뺀 값이다")
    void commissionAndPayout() {
        Artist artist = givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artist, "100000", LAST_MONTH);

        SettlementDto.ArtistSettlementResponse item =
                settlementService.getPeriod(PERIOD).items().get(0);

        assertThat(item.commissionAmount()).isEqualByComparingTo("20000");
        assertThat(item.payoutAmount()).isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("수수료 + 지급액 = 순매출. 1원도 새지 않는다")
    void amountsAlwaysReconcile() {
        Artist artist = givenArtist("김작가", "0.3333");
        givenDeliveredOrder(artist, "123457", LAST_MONTH);

        SettlementDto.ArtistSettlementResponse item =
                settlementService.getPeriod(PERIOD).items().get(0);

        assertThat(item.commissionAmount().add(item.payoutAmount()))
                .isEqualByComparingTo(item.netAmount());
    }

    @Test
    @DisplayName("작가마다 수수료율이 다르게 적용된다")
    void perArtistCommissionRate() {
        Artist cheap = givenArtist("김작가", "0.1000");
        Artist pricey = givenArtist("이작가", "0.3000");
        givenDeliveredOrder(cheap, "100000", LAST_MONTH);
        givenDeliveredOrder(pricey, "100000", LAST_MONTH);

        List<SettlementDto.ArtistSettlementResponse> items =
                settlementService.getPeriod(PERIOD).items();

        assertThat(payoutOf(items, cheap.getId())).isEqualByComparingTo("90000");
        assertThat(payoutOf(items, pricey.getId())).isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("승인된 반품은 그 달의 차감으로 잡힌다")
    void approvedReturnIsDeducted() {
        Artist artist = givenArtist("김작가", "0.2000");
        Order order = givenDeliveredOrder(artist, "100000", LAST_MONTH);
        givenApprovedReturn(order, "30000", LAST_MONTH);

        SettlementDto.ArtistSettlementResponse item =
                settlementService.getPeriod(PERIOD).items().get(0);

        assertThat(item.refundAmount()).isEqualByComparingTo("30000");
        assertThat(item.netAmount()).isEqualByComparingTo("70000");
        assertThat(item.payoutAmount()).isEqualByComparingTo("56000");
    }

    @Test
    @DisplayName("승인 전 반품은 차감하지 않는다 — 반려될 수 있다")
    void pendingReturnIsNotDeducted() {
        Artist artist = givenArtist("김작가", "0.2000");
        Order order = givenDeliveredOrder(artist, "100000", LAST_MONTH);
        givenRequestedReturn(order);

        assertThat(settlementService.getPeriod(PERIOD).items().get(0).refundAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("교환은 차감하지 않는다 — 물건이 다시 나가므로 매출이 사라지지 않는다")
    void exchangeIsNotDeducted() {
        Artist artist = givenArtist("김작가", "0.2000");
        Order order = givenDeliveredOrder(artist, "100000", LAST_MONTH);
        givenApprovedExchange(order, "100000", LAST_MONTH);

        assertThat(settlementService.getPeriod(PERIOD).items().get(0).refundAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("한 주문에 두 작가가 섞여 있으면 환불액을 금액 비중대로 나눈다")
    void refundIsProratedAcrossArtists() {
        Artist a = givenArtist("김작가", "0.2000");
        Artist b = givenArtist("이작가", "0.2000");

        Order order = givenDeliveredOrderWithTwoArtists(a, "80000", b, "20000", LAST_MONTH);
        givenApprovedReturn(order, "100000", LAST_MONTH);

        List<SettlementDto.ArtistSettlementResponse> items =
                settlementService.getPeriod(PERIOD).items();

        assertThat(refundOf(items, a.getId())).isEqualByComparingTo("80000");
        assertThat(refundOf(items, b.getId())).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("매출 없이 반품만 있는 작가도 정산 목록에 나온다 — 지난달 판매분이 이번 달 반품된 경우")
    void refundOnlyArtistStillAppears() {
        Artist artist = givenArtist("김작가", "0.2000");
        Order order = givenDeliveredOrder(artist, "100000", LAST_MONTH.minusMonths(1));
        givenApprovedReturn(order, "100000", LAST_MONTH);

        SettlementDto.PeriodSummaryResponse result = settlementService.getPeriod(PERIOD);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).grossAmount()).isEqualByComparingTo("0");
        assertThat(result.items().get(0).netAmount()).isEqualByComparingTo("-100000");
    }

    @Test
    @DisplayName("확정하면 금액이 굳는다 — 이후 반품이 들어와도 바뀌지 않는다")
    void confirmedAmountsAreFrozen() {
        Artist artist = givenArtist("김작가", "0.2000");
        Order order = givenDeliveredOrder(artist, "100000", LAST_MONTH);

        settlementService.confirm(PERIOD);
        BigDecimal afterConfirm = settlementService.getPeriod(PERIOD).items().get(0).payoutAmount();

        givenApprovedReturn(order, "50000", LAST_MONTH);

        assertThat(settlementService.getPeriod(PERIOD).items().get(0).payoutAmount())
                .isEqualByComparingTo(afterConfirm)
                .isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("같은 달을 두 번 확정할 수 없다 — 이중 지급을 막는다")
    void cannotConfirmTwice() {
        givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artistRepository.findAll().get(0), "100000", LAST_MONTH);
        settlementService.confirm(PERIOD);

        assertThatThrownBy(() -> settlementService.confirm(PERIOD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 확정");
    }

    @Test
    @DisplayName("아직 끝나지 않은 달은 확정할 수 없다")
    void cannotConfirmCurrentMonth() {
        assertThatThrownBy(() -> settlementService.confirm(YearMonth.now().toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("끝나지 않은");
    }

    @Test
    @DisplayName("지급액이 0 이하인 작가는 확정에서 빠진다 — 다음 달로 넘길 빚이라 사람이 봐야 한다")
    void nonPositivePayoutIsExcludedFromConfirm() {
        Artist artist = givenArtist("김작가", "0.2000");
        Order order = givenDeliveredOrder(artist, "100000", LAST_MONTH.minusMonths(1));
        givenApprovedReturn(order, "100000", LAST_MONTH);

        settlementService.confirm(PERIOD);

        assertThat(settlementRepository.findByPeriodYm(PERIOD)).isEmpty();
    }

    @Test
    @DisplayName("지급 완료를 두 번 누를 수 없다")
    void cannotPayTwice() {
        Artist artist = givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artist, "100000", LAST_MONTH);
        settlementService.confirm(PERIOD);
        Long id = settlementRepository.findByPeriodYm(PERIOD).get(0).getId();

        settlementService.markPaid(id, "계좌이체");

        assertThatThrownBy(() -> settlementService.markPaid(id, "또 이체"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 지급");
    }

    @Test
    @DisplayName("확정 전에는 확정 여부가 false 로 내려간다")
    void unconfirmedIsFlagged() {
        Artist artist = givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artist, "100000", LAST_MONTH);

        assertThat(settlementService.getPeriod(PERIOD).confirmed()).isFalse();
        settlementService.confirm(PERIOD);
        assertThat(settlementService.getPeriod(PERIOD).confirmed()).isTrue();
    }

    @Test
    @DisplayName("수수료율은 0 이상 1 미만만 허용한다")
    void commissionRateBounds() {
        Artist artist = givenArtist("김작가", "0.2000");

        assertThatThrownBy(() -> settlementService.changeCommissionRate(artist.getId(), new BigDecimal("1.0")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> settlementService.changeCommissionRate(artist.getId(), new BigDecimal("-0.1")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> settlementService.changeCommissionRate(artist.getId(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("요율을 바꿔도 이미 확정된 달의 금액은 그대로다")
    void rateChangeDoesNotAffectConfirmed() {
        Artist artist = givenArtist("김작가", "0.2000");
        givenDeliveredOrder(artist, "100000", LAST_MONTH);
        settlementService.confirm(PERIOD);

        settlementService.changeCommissionRate(artist.getId(), new BigDecimal("0.5000"));

        assertThat(settlementService.getPeriod(PERIOD).items().get(0).payoutAmount())
                .isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("정산 월 형식이 잘못되면 거절한다")
    void invalidPeriodFormat() {
        assertThatThrownBy(() -> settlementService.getPeriod("2026/08"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("정산할 것이 없으면 빈 목록이다 — 예외가 아니다")
    void emptyPeriod() {
        SettlementDto.PeriodSummaryResponse result = settlementService.getPeriod(PERIOD);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalPayout()).isEqualByComparingTo("0");
    }

    private Artist givenArtist(String name, String rate) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Artist artist = artistRepository.save(Artist.builder()
                .artistCode("A-" + unique)
                .name(name)
                .slug("slug-" + unique)
                .build());
        artist.changeCommissionRate(new BigDecimal(rate));
        return artistRepository.saveAndFlush(artist);
    }

    private Order givenDeliveredOrder(Artist artist, String amount, YearMonth deliveredIn) {
        return saveOrder(artist, amount, null, null, "DELIVERED", deliveredIn);
    }

    private Order givenDeliveredOrderWithTwoArtists(Artist a, String amountA,
                                                    Artist b, String amountB,
                                                    YearMonth deliveredIn) {
        return saveOrder(a, amountA, b, amountB, "DELIVERED", deliveredIn);
    }

    private Order givenShippedOrder(Artist artist, String amount, YearMonth shippedIn) {
        return saveOrder(artist, amount, null, null, "SHIPPED", shippedIn);
    }

    private Order saveOrder(Artist artistA, String amountA, Artist artistB, String amountB,
                            String orderStatus, YearMonth deliveredIn) {
        BigDecimal total = new BigDecimal(amountA)
                .add(amountB != null ? new BigDecimal(amountB) : BigDecimal.ZERO);

        Order order = Order.builder()
                .orderNo("ORD-" + UUID.randomUUID().toString().substring(0, 12))
                .user(givenUser())
                .currency("KRW")
                .productAmount(total)
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(total)
                .ordererName("홍길동")
                .ordererEmail("test@example.com")
                .ordererPhone("01000000000")
                .build();

        if ("DELIVERED".equals(orderStatus)) order.markDelivered();
        else order.markShipped();

        order.getOrderItems().add(orderItem(order, artistA, amountA));
        if (artistB != null) {
            order.getOrderItems().add(orderItem(order, artistB, amountB));
        }
        orderRepository.saveAndFlush(order);

        orderShipmentRepository.saveAndFlush(OrderShipment.builder()
                .order(order)
                .recipientName("홍길동")
                .recipientPhone("01000000000")
                .zipCode("10000")
                .address1("서울시")
                .build());

        jdbcTemplate.update("UPDATE order_shipments SET delivered_at = ? WHERE order_id = ?",
                midMonth(deliveredIn), order.getId());

        return order;
    }

    private OrderItem orderItem(Order order, Artist artist, String amount) {
        return OrderItem.builder()
                .order(order)
                .artist(artist)
                .skuCodeSnapshot("SKU-" + UUID.randomUUID().toString().substring(0, 8))
                .artistCodeSnapshot(artist.getArtistCode())
                .skuNameSnapshot("작품")
                .artistNameSnapshot(artist.getName())
                .quantity(1)
                .unitPrice(new BigDecimal(amount))
                .lineTotalAmount(new BigDecimal(amount))
                .build();
    }

    private void givenApprovedReturn(Order order, String refundAmount, YearMonth processedIn) {
        ReturnRequest rr = saveReturn(order, "RETURN");
        rr.approve(new BigDecimal(refundAmount), "승인");
        returnRequestRepository.saveAndFlush(rr);
        jdbcTemplate.update("UPDATE return_requests SET processed_at = ? WHERE id = ?",
                midMonth(processedIn), rr.getId());
    }

    private void givenApprovedExchange(Order order, String refundAmount, YearMonth processedIn) {
        ReturnRequest rr = saveReturn(order, "EXCHANGE");
        rr.approve(new BigDecimal(refundAmount), "승인");
        returnRequestRepository.saveAndFlush(rr);
        jdbcTemplate.update("UPDATE return_requests SET processed_at = ? WHERE id = ?",
                midMonth(processedIn), rr.getId());
    }

    private void givenRequestedReturn(Order order) {
        returnRequestRepository.saveAndFlush(saveReturn(order, "RETURN"));
    }

    private ReturnRequest saveReturn(Order order, String type) {
        return ReturnRequest.builder()
                .returnNo("RET-" + UUID.randomUUID().toString().substring(0, 12))
                .order(order)
                .user(order.getUser())
                .returnType(type)
                .reason("CHANGE_OF_MIND")
                .build();
    }

    private User givenUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .userCode("UTEST-" + unique)
                .email("buyer-" + unique + "@koala.test")
                .passwordHash("{noop}test")
                .name("홍길동")
                .phone("01000000000")
                .build());
    }

    private LocalDateTime midMonth(YearMonth ym) {
        return ym.atDay(15).atTime(12, 0);
    }

    private BigDecimal payoutOf(List<SettlementDto.ArtistSettlementResponse> items, Long artistId) {
        return items.stream().filter(i -> i.artistId().equals(artistId))
                .findFirst().orElseThrow().payoutAmount();
    }

    private BigDecimal refundOf(List<SettlementDto.ArtistSettlementResponse> items, Long artistId) {
        return items.stream().filter(i -> i.artistId().equals(artistId))
                .findFirst().orElseThrow().refundAmount();
    }
}
