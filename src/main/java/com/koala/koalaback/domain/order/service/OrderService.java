package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.cart.entity.Cart;
import com.koala.koalaback.domain.cart.entity.CartItem;
import com.koala.koalaback.domain.cart.service.CartService;
import com.koala.koalaback.domain.order.dto.OrderDto;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final CartService cartService;
    private final StockService stockService;
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final CodeGenerator codeGenerator;
    private final PhoneNormalizer phoneNormalizer;

    private final OrderTransactionService orderTransactionService;

    @Transactional
    public OrderDto.OrderDetailResponse createOrder(Long userId, OrderDto.CreateRequest req) {
        Cart cart = cartService.getOrCreateCart(userId);

        List<CartItem> selectedItems = selectCartItems(cart, req.getCartItemIds());
        if (selectedItems.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<CartItem> lockOrderedItems = sortByLockOrder(selectedItems);

        log.debug("재고 차감 락 획득 순서(skuId 오름차순): {}",
                lockOrderedItems.stream().map(ci -> ci.getSku().getId()).toList());

        for (CartItem ci : lockOrderedItems) {
            Sku sku = ci.getSku();
            if (!sku.isAvailable()) throw new BusinessException(ErrorCode.SKU_NOT_ACTIVE);
            stockService.deduct(sku.getId(), ci.getQuantity(), "order_items", null);
        }

        BigDecimal productAmount = selectedItems.stream()
                .map(CartItem::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingAmount = productAmount.compareTo(new BigDecimal("50000")) >= 0
                ? BigDecimal.ZERO : new BigDecimal("3000");
        BigDecimal totalAmount = productAmount.add(shippingAmount);

        String phone = phoneNormalizer.normalize(req.getOrdererPhone());

        Order order = Order.builder()
                .orderNo(codeGenerator.generateOrderNo())
                .user(userService.getUserById(userId))
                .productAmount(productAmount)
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(shippingAmount)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .ordererName(req.getOrdererName())
                .ordererEmail(req.getOrdererEmail())
                .ordererPhone(phone)
                .build();
        orderRepository.save(order);

        List<OrderItem> orderItems = selectedItems.stream().map(ci -> {
            Sku sku = ci.getSku();
            return OrderItem.builder()
                    .order(order)
                    .sku(sku)
                    .artist(sku.getArtist())
                    .skuCodeSnapshot(sku.getSkuCode())
                    .artistCodeSnapshot(sku.getArtist().getArtistCode())
                    .skuNameSnapshot(sku.getName())
                    .artistNameSnapshot(sku.getArtist().getName())
                    .quantity(ci.getQuantity())
                    .unitPrice(ci.getUnitPrice())
                    .lineTotalAmount(ci.getLineAmount())
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

        cart.getItems().removeAll(selectedItems);

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

    /**
     * 주문 취소 — 환불 후 재고를 되돌리고 주문을 닫는다.
     *
     * <p><b>이 메서드는 트랜잭션을 열지 않는다.</b> 중간에 PG 로 HTTP 환불 요청을 보내는데,
     * 그 호출을 DB 트랜잭션 안에 두면 PG 응답이 늦어지는 동안 커넥션과 행 잠금이 함께 묶인다.
     * 단계마다 필요한 트랜잭션은 {@code orderTransactionService} 가 각자 연다.
     *
     * <p>표시를 빼면 클래스에 걸린 {@code readOnly = true} 가 그대로 적용된다. 그러면 안쪽의
     * {@code @Transactional} 이 <b>새 트랜잭션을 열지 않고 읽기 전용 트랜잭션에 합류</b>해,
     * 재고를 되돌리며 거는 {@code SELECT ... FOR UPDATE} 가 DB 에서 거부된다.
     * 실제로 그 이유로 주문 취소가 500 으로 실패했다.
     */
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

    /**
     * 어드민 강제 취소.
     *
     * <p>사용자 취소와 같은 이유로 트랜잭션을 열지 않는다 — {@link #cancelOrder} 설명 참고.
     */
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

    public Order getOrderEntityByNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    static List<CartItem> sortByLockOrder(List<CartItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(ci -> ci.getSku().getId()))
                .toList();
    }

    private List<CartItem> selectCartItems(Cart cart, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return cart.getItems();
        return cart.getItems().stream()
                .filter(ci -> itemIds.contains(ci.getId()))
                .toList();
    }
}
