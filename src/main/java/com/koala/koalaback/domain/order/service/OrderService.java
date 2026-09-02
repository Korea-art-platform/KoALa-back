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

        // 장바구니에 담긴 단가는 부가세를 뺀 공급가액이다. 고객이 내는 금액은
        // 여기에 부가세를 더한 값이고, 화면에 보여 준 금액과 같아야 한다.
        // 원작처럼 면세로 표시된 분류에는 붙지 않는다.
        Set<String> exempt = vatPolicy.exemptMainCategories();
        Map<Long, VatPolicy.Line> lines = selectedItems.stream().collect(Collectors.toMap(
                CartItem::getId,
                ci -> vatPolicy.lineOf(ci.getUnitPrice(), ci.getQuantity(),
                        ci.getSku().getMainCategory(), exempt)));

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
                .user(userService.getUserById(userId))
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
                    .taxAmount(lines.get(ci.getId()).tax())
                    .lineTotalAmount(lines.get(ci.getId()).gross())
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
