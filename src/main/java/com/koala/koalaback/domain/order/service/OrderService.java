package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.cart.entity.Cart;
import com.koala.koalaback.domain.cart.entity.CartItem;
import com.koala.koalaback.domain.cart.service.CartService;
import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import com.koala.koalaback.domain.order.repository.OrderItemRepository;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.order.repository.OrderShipmentRepository;
import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.domain.sku.entity.Sku;
import com.koala.koalaback.domain.sku.service.SkuService;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.global.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final VatPolicy vatPolicy;
    private final OrderItemRepository orderItemRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final CartService cartService;
    private final StockService stockService;
    private final SkuService skuService;
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final CodeGenerator codeGenerator;
    private final PhoneNormalizer phoneNormalizer;

    private final OrderTransactionService orderTransactionService;

    @Transactional
    public OrderDto.OrderDetailResponse createOrder(Long userId, OrderDto.CreateRequest req) {
        // 담은 것 중 고른 것으로 주문하거나, 장바구니를 거치지 않고 한 건만 산다.
        // 사는 물건을 어디서 가져오는지만 다르고 금액·재고·결제는 같은 길을 탄다.
        //
        // 비회원에게는 장바구니가 없다. 한 건씩만 살 수 있으므로 담긴 것을
        // 꺼낼 일도 없다.
        boolean guest = userId == null;
        if (guest && req.getDirectItem() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비회원은 상품을 하나씩만 주문할 수 있습니다.");
        }

        Cart cart = guest ? null : cartService.getOrCreateCart(userId);
        List<CartItem> selectedItems = req.getDirectItem() != null
                ? List.of()
                : selectCartItems(cart, req.getCartItemIds());
        List<OrderLine> orderLines = req.getDirectItem() != null
                ? List.of(directLine(req.getDirectItem()))
                : selectedItems.stream().map(OrderLine::of).toList();

        if (orderLines.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<OrderLine> lockOrdered = orderLines.stream()
                .sorted(java.util.Comparator.comparing(l -> l.sku().getId()))
                .toList();

        log.debug("재고 차감 락 획득 순서(skuId 오름차순): {}",
                lockOrdered.stream().map(l -> l.sku().getId()).toList());

        for (OrderLine l : lockOrdered) {
            if (!l.sku().isAvailable()) throw new BusinessException(ErrorCode.SKU_NOT_ACTIVE);
            stockService.deduct(l.sku().getId(), l.quantity(), "order_items", null);
        }

        // 저장된 단가는 부가세를 뺀 공급가액이다. 고객이 내는 금액은 여기에
        // 부가세를 더한 값이고, 화면에 보여 준 금액과 같아야 한다.
        // 원작처럼 면세로 표시된 분류에는 붙지 않는다.
        Set<String> exempt = vatPolicy.exemptMainCategories();
        Map<OrderLine, VatPolicy.Line> lines = new java.util.LinkedHashMap<>();
        for (OrderLine l : orderLines) {
            lines.put(l, vatPolicy.lineOf(l.unitPrice(), l.quantity(),
                    l.sku().getMainCategory(), exempt));
        }

        BigDecimal productAmount = lines.values().stream()
                .map(VatPolicy.Line::supply).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productTax = lines.values().stream()
                .map(VatPolicy.Line::tax).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productGross = productAmount.add(productTax);

        // 무료배송은 고객이 보는 금액으로 판단한다. "5만원 이상 무료"의 5만원은
        // 장바구니에 찍힌 그 숫자다.
        BigDecimal shippingAmount = productGross.compareTo(new BigDecimal("50000")) >= 0
                ? BigDecimal.ZERO : new BigDecimal("3000");

        // 배송비는 부가세가 포함된 금액이다. 고객이 내는 3,000원은 그대로 두고
        // 그 안에 든 세액만 장부에 남긴다.
        BigDecimal shippingTax = vatPolicy.vatIncludedIn(shippingAmount);

        BigDecimal taxAmount = productTax.add(shippingTax);
        BigDecimal totalAmount = productGross.add(shippingAmount);

        String phone = phoneNormalizer.normalize(req.getOrdererPhone());

        Order order = Order.builder()
                .orderNo(codeGenerator.generateOrderNo())
                .user(guest ? null : userService.getUserById(userId))
                .productAmount(productAmount)
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(shippingAmount)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .ordererName(req.getOrdererName())
                .ordererEmail(req.getOrdererEmail())
                .ordererPhone(phone)
                .build();
        orderRepository.save(order);

        List<OrderItem> orderItems = orderLines.stream().map(l -> {
            Sku sku = l.sku();
            return OrderItem.builder()
                    .order(order)
                    .sku(sku)
                    .artist(sku.getArtist())
                    .skuCodeSnapshot(sku.getSkuCode())
                    .artistCodeSnapshot(sku.getArtist().getArtistCode())
                    .skuNameSnapshot(sku.getName())
                    .artistNameSnapshot(sku.getArtist().getName())
                    .quantity(l.quantity())
                    .unitPrice(l.unitPrice())
                    .taxAmount(lines.get(l).tax())
                    .lineTotalAmount(lines.get(l).gross())
                    .build();
        }).toList();
        orderItemRepository.saveAll(orderItems);
        order.getOrderItems().addAll(orderItems);

        OrderDto.ShipmentRequest sr = req.getShipment();
        String shipPhone = phoneNormalizer.normalize(sr.getRecipientPhone());
        OrderShipment shipment = OrderShipment.builder()
                .order(order)
                .recipientName(sr.getRecipientName())
                .recipientPhone(shipPhone)
                .zipCode(sr.getZipCode())
                .address1(sr.getAddress1())
                .address2(sr.getAddress2())
                .deliveryRequest(sr.getDeliveryRequest())
                .build();
        orderShipmentRepository.save(shipment);

        // 바로구매·비회원은 장바구니를 거치지 않았으니 비울 것도 없다.
        if (cart != null && !selectedItems.isEmpty()) {
            cart.getItems().removeAll(selectedItems);
        }

        log.info("Order created: orderNo={}, userId={}, total={}",
                order.getOrderNo(), userId, totalAmount);
        return OrderDto.OrderDetailResponse.from(order);
    }

    public PageResponse<OrderDto.OrderSummaryResponse> getMyOrders(Long userId, Pageable pageable) {
        return PageResponse.of(
                orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(OrderDto.OrderSummaryResponse::from)
        );
    }

    public OrderDto.OrderDetailResponse getMyOrder(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OrderDto.OrderDetailResponse.from(order);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderDto.OrderDetailResponse cancelOrder(Long userId, String orderNo) {
        String refundPaymentNo = orderTransactionService.checkCancellable(userId, orderNo);

        if (refundPaymentNo != null) {
            paymentService.cancel(refundPaymentNo, new PaymentDto.CancelRequest("주문취소", null));
            log.info("Payment refunded on order cancel: paymentNo={}", refundPaymentNo);
        }

        return orderTransactionService.completeCancel(userId, orderNo);
    }

    @Transactional
    public void expirePendingOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;
        if (!"PENDING_PAYMENT".equals(order.getOrderStatus())) return;

        boolean settlementPending = paymentRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> p.isSettlementPending())
                .isPresent();
        if (settlementPending) {
            log.warn("결제 확정 대기 중이라 만료 취소 건너뜀: orderNo={}", order.getOrderNo());
            return;
        }

        order.getOrderItems().stream()
                .filter(item -> item.getSku() != null)
                .sorted(Comparator.comparing(item -> item.getSku().getId()))
                .forEach(item -> stockService.restore(item.getSku().getId(), item.getQuantity(),
                        "order_expiry", item.getId()));
        order.cancel();
        log.info("미결제 만료 주문 자동취소: orderNo={}", order.getOrderNo());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderDto.OrderDetailResponse adminCancelOrder(String orderNo, OrderDto.AdminCancelRequest req) {
        String refundPaymentNo = orderTransactionService.forceCancelAndFindRefundTarget(orderNo);
        log.info("Admin force cancel: orderNo={}, reason={}", orderNo, req.getReason());

        if (refundPaymentNo != null) {
            try {
                paymentService.cancel(refundPaymentNo,
                        new PaymentDto.CancelRequest(req.getReason(), req.getCancelAmount()));
                log.info("Admin refund success: paymentNo={}, amount={}",
                        refundPaymentNo, req.getCancelAmount());
            } catch (Exception e) {
                log.error("Admin refund FAILED — manual action required: paymentNo={}, orderNo={}, error={}",
                        refundPaymentNo, orderNo, e.getMessage());
                paymentService.recordRefundFailure(refundPaymentNo,
                        "어드민 강제취소 환불 실패 — 수동처리 필요: " + e.getMessage());
            }
        }

        return orderTransactionService.getOrderDetail(orderNo);
    }

    public OrderDto.OrderDetailResponse getAdminOrderDetail(String orderNo) {
        return OrderDto.OrderDetailResponse.from(getOrderEntityByNo(orderNo));
    }

    public PageResponse<OrderDto.OrderSummaryResponse> getAdminOrders(Pageable pageable) {
        return PageResponse.of(
                orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                        .map(OrderDto.OrderSummaryResponse::from)
        );
    }

    public PageResponse<OrderDto.OrderSummaryResponse> adminSearchOrders(
            Long userId, String name, String phone, Pageable pageable) {
        return PageResponse.of(
                orderRepository.searchOrders(
                        userId,
                        (name  != null && !name.isBlank())  ? name  : null,
                        (phone != null && !phone.isBlank()) ? phone : null,
                        pageable
                ).map(OrderDto.OrderSummaryResponse::from)
        );
    }

    @Transactional
    public void registerTracking(String orderNo, OrderDto.RegisterTrackingRequest req) {
        Order order = getOrderEntityByNo(orderNo);
        OrderShipment shipment = orderShipmentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        shipment.registerTracking(req.getCarrierCode(), req.getTrackingNo());
        order.markShipped();
    }

    @Transactional
    public void markDelivered(String orderNo) {
        Order order = getOrderEntityByNo(orderNo);
        order.markDelivered();
        OrderShipment shipment = orderShipmentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        shipment.markDelivered();
    }

    /**
     * 비회원이 자기 주문을 찾는다.
     *
     * 로그인이 없으니 주문번호와 주문할 때 적은 전화번호가 맞는지로 본인을
     * 가린다. 주문번호만으로는 열어 주지 않는다 — 번호는 시각에서 만들어져
     * 앞자리를 짐작할 수 있다.
     *
     * 없는 주문과 전화번호가 틀린 경우를 같은 말로 돌려준다. 다르게 답하면
     * 번호를 넣어 보는 것만으로 어떤 주문이 있는지 알아낼 수 있다.
     * 반복 시도는 RateLimitFilter 가 로그인과 같은 급으로 막는다.
     */
    public OrderDto.OrderDetailResponse getGuestOrder(String orderNo, String phone) {
        String normalized = phoneNormalizer.normalize(phone);
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(o -> o.getOrdererPhone() != null
                        && o.getOrdererPhone().equals(normalized))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND,
                        "주문번호나 휴대폰번호가 맞지 않습니다."));

        log.info("비회원 주문 조회: orderNo={}", orderNo);
        return OrderDto.OrderDetailResponse.from(order);
    }

    /**
     * 가입할 때, 같은 이메일로 했던 비회원 주문을 계정에 붙인다.
     *
     * 이메일은 가입 과정에서 확인된 값이라 그 사람 것이 맞다. 이미 주인이
     * 있는 주문은 Order.attachTo 가 걸러 낸다.
     */
    @Transactional
    public int linkGuestOrders(Long userId, String email) {
        if (email == null || email.isBlank()) return 0;

        List<Order> orders = orderRepository.findByOrdererEmailAndUserIsNull(email);
        if (orders.isEmpty()) return 0;

        var owner = userService.getUserById(userId);
        orders.forEach(o -> o.attachTo(owner));

        log.info("비회원 주문을 계정에 붙였다: userId={}, 건수={}", userId, orders.size());
        return orders.size();
    }

    public Order getOrderEntityByNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    static List<CartItem> sortByLockOrder(List<CartItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(ci -> ci.getSku().getId()))
                .toList();
    }

    /**
     * 주문에 들어갈 한 줄. 장바구니에서 왔든 바로구매든 여기서부터는 같다.
     *
     * 값으로 견주면 같은 상품 두 줄이 하나로 뭉개진다. 지금은 한 주문에 같은
     * 상품이 두 줄로 들어올 일이 없지만, 나중에 옵션이 생기면 생긴다.
     */
    private record OrderLine(Sku sku, int quantity, BigDecimal unitPrice) {
        static OrderLine of(CartItem ci) {
            return new OrderLine(ci.getSku(), ci.getQuantity(), ci.getUnitPrice());
        }
        @Override public boolean equals(Object o) { return this == o; }
        @Override public int hashCode() { return System.identityHashCode(this); }
    }

    /**
     * 바로구매 한 줄을 만든다.
     *
     * 단가는 요청이 아니라 지금 저장된 값을 쓴다. 화면에서 보낸 값을 믿으면
     * 값을 고쳐 보내는 것만으로 싸게 살 수 있다.
     */
    private OrderLine directLine(OrderDto.DirectItemRequest d) {
        Sku sku = skuService.getSkuEntityByCode(d.getSkuCode());
        int quantity = d.getQuantity() == null || d.getQuantity() < 1 ? 1 : d.getQuantity();
        return new OrderLine(sku, quantity, sku.getEffectivePrice());
    }

    private List<CartItem> selectCartItems(Cart cart, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return cart.getItems();
        return cart.getItems().stream()
                .filter(ci -> itemIds.contains(ci.getId()))
                .toList();
    }
}
