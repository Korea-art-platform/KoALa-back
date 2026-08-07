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
    /** 취소 흐름의 DB 단계 — 자기호출을 피하려고 별도 빈으로 분리했다 */
    private final OrderTransactionService orderTransactionService;

    @Transactional
    public OrderDto.OrderDetailResponse createOrder(Long userId, OrderDto.CreateRequest req) {
        Cart cart = cartService.getOrCreateCart(userId);

        List<CartItem> selectedItems = selectCartItems(cart, req.getCartItemIds());
        if (selectedItems.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 재고 검증 및 차감.
        //
        // deduct() 는 SKU row 에 비관적 락을 건다. 장바구니 순서대로 잡으면
        // A주문이 [1,2], B주문이 [2,1] 순으로 잡을 때 서로 상대의 락을 기다리는 데드락이 난다.
        // 모든 주문이 skuId 오름차순이라는 같은 순서로 잡으면 순환 대기가 생기지 않는다.
        List<CartItem> lockOrderedItems = sortByLockOrder(selectedItems);

        log.debug("재고 차감 락 획득 순서(skuId 오름차순): {}",
                lockOrderedItems.stream().map(ci -> ci.getSku().getId()).toList());

        for (CartItem ci : lockOrderedItems) {
            Sku sku = ci.getSku();
            if (!sku.isAvailable()) throw new BusinessException(ErrorCode.SKU_NOT_ACTIVE);
            stockService.deduct(sku.getId(), ci.getQuantity(), "order_items", null);
        }

        // 금액 계산
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

        // 주문 아이템 저장
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

        // 배송지 저장
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

        // 장바구니에서 주문 완료 아이템 제거
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
     * 주문 취소 — 환불이 성공해야 취소된다.
     *
     * <p><b>트랜잭션이 걸려 있지 않다.</b> 환불은 외부 PG HTTP 호출이라
     * 트랜잭션 안에서 부르면 응답을 기다리는 내내 DB 커넥션이 묶인다.
     * <pre>
     * ① checkCancellable  [트랜잭션] 취소 가능 여부 확인 + 환불 대상 결제 조회 → 커밋
     * ② paymentService.cancel  [트랜잭션 밖] PG 환불 (자체적으로 짧은 트랜잭션 사용)
     * ③ completeCancel    [트랜잭션] 재고 복구 + 주문 취소 → 커밋
     * </pre>
     *
     * <p>환불이 실패하면 ②에서 예외가 나고 ③에 도달하지 않으므로,
     * "취소됐는데 환불 안 된" 주문은 생기지 않는다.
     */
    public OrderDto.OrderDetailResponse cancelOrder(Long userId, String orderNo) {
        // ① 취소 가능 여부 확인 + 환불 대상 파악
        String refundPaymentNo = orderTransactionService.checkCancellable(userId, orderNo);

        // ② 환불 먼저 — 트랜잭션 밖. 실패하면 예외가 전파되어 주문은 그대로 남는다.
        if (refundPaymentNo != null) {
            paymentService.cancel(refundPaymentNo, new PaymentDto.CancelRequest("주문취소", null));
            log.info("Payment refunded on order cancel: paymentNo={}", refundPaymentNo);
        }

        // ③ 환불 성공(또는 결제 없음) 후 재고 복구 및 주문 취소
        return orderTransactionService.completeCancel(userId, orderNo);
    }

    /**
     * 미결제 만료 주문 1건 자동 취소 + 재고 복구 (스케줄러 전용).
     * <p>각 주문을 독립 트랜잭션으로 처리한다. 조회 후 결제됐을 수 있으므로
     * 트랜잭션 안에서 상태를 재확인하고, PENDING_PAYMENT 가 아니면 건드리지 않는다.
     * 결제 전 주문이라 환불 대상(CAPTURED 결제)은 없다.
     */
    @Transactional
    public void expirePendingOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;
        if (!"PENDING_PAYMENT".equals(order.getOrderStatus())) return; // 그 사이 결제/취소됨

        // 승인 여부가 미확정인 결제가 붙어 있으면 절대 취소하지 않는다.
        // 실제로는 승인되어 돈이 빠져나갔을 수 있어, 여기서 취소하면 "결제됐는데 주문 없음" 이 된다.
        boolean settlementPending = paymentRepository
                .findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> p.isSettlementPending())
                .isPresent();
        if (settlementPending) {
            log.warn("결제 확정 대기 중이라 만료 취소 건너뜀: orderNo={}", order.getOrderNo());
            return;
        }

        order.getOrderItems().forEach(item -> {
            if (item.getSku() != null) {
                stockService.restore(item.getSku().getId(), item.getQuantity(),
                        "order_expiry", item.getId());
            }
        });
        order.cancel();
        log.info("미결제 만료 주문 자동취소: orderNo={}", order.getOrderNo());
    }

    /**
     * 관리자 강제 취소 — 모든 상태 취소 가능, 이유 필수, 부분환불 지원.
     *
     * <p>주문 취소와 달리 환불은 best-effort 다(강제 취소는 어떤 상태에서든 완료되어야 함).
     * 환불이 실패해도 주문은 취소된 채로 두고, 실패 사실을 이벤트로 남겨 수동 처리하게 한다.
     * PG 호출은 트랜잭션 밖에서 이루어진다.
     */
    public OrderDto.OrderDetailResponse adminCancelOrder(String orderNo, OrderDto.AdminCancelRequest req) {
        // ① 재고 복구 + 강제 취소 (트랜잭션)
        String refundPaymentNo = orderTransactionService.forceCancelAndFindRefundTarget(orderNo);
        log.info("Admin force cancel: orderNo={}, reason={}", orderNo, req.getReason());

        // ② 환불 — 트랜잭션 밖, 실패해도 취소는 유지
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

    /** 관리자 주문 상세 (트랜잭션 안에서 lazy 컬렉션 접근) */
    public OrderDto.OrderDetailResponse getAdminOrderDetail(String orderNo) {
        return OrderDto.OrderDetailResponse.from(getOrderEntityByNo(orderNo));
    }

    /** 관리자 전체 주문 목록 (트랜잭션 안에서 lazy 컬렉션 접근) */
    public PageResponse<OrderDto.OrderSummaryResponse> getAdminOrders(Pageable pageable) {
        return PageResponse.of(
                orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                        .map(OrderDto.OrderSummaryResponse::from)
        );
    }

    /** 관리자 주문 검색 — 회원ID / 주문자명 / 전화번호 */
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

    /**
     * 재고 락을 잡을 순서로 정렬 — skuId 오름차순.
     *
     * <p>모든 주문이 같은 순서로 락을 잡아야 순환 대기(데드락)가 생기지 않는다.
     * 정렬된 새 리스트를 반환하므로 원본(금액 계산·주문 아이템 생성용)의 순서는 유지된다.
     */
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