package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.artist.entity.Artist;
import com.koala.koalaback.domain.artist.service.ArtistService;
import com.koala.koalaback.domain.order.dto.OnSitePaymentDto;
import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.entity.OrderItem;
import com.koala.koalaback.domain.order.entity.OrderShipment;
import com.koala.koalaback.domain.order.repository.OrderItemRepository;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.order.repository.OrderShipmentRepository;
import com.koala.koalaback.domain.pricing.VatPolicy;
import com.koala.koalaback.global.crypto.PiiIndex;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.util.CodeGenerator;
import com.koala.koalaback.global.util.PhoneNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnSitePaymentService {
    private static final String ON_SITE = "ON_SITE";
    private static final String DEFAULT_BUYER_NAME = "현장 구매";
    private static final String DEFAULT_EMAIL = "koala-art@heron.kr";
    private static final String DEFAULT_PHONE = "+8218332817";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final ArtistService artistService;
    private final VatPolicy vatPolicy;
    private final CodeGenerator codeGenerator;
    private final PhoneNormalizer phoneNormalizer;
    private final PiiIndex piiIndex;

    @Transactional
    public OnSitePaymentDto.Response create(OnSitePaymentDto.CreateRequest req) {
        Artist artist = artistService.getArtistEntityByCode(req.getArtistCode());

        BigDecimal gross = BigDecimal.valueOf(req.getAmount());
        BigDecimal tax = Boolean.TRUE.equals(req.getTaxExempt())
                ? BigDecimal.ZERO : vatPolicy.vatIncludedIn(gross);
        BigDecimal supply = gross.subtract(tax);

        String name = hasText(req.getBuyerName()) ? req.getBuyerName().trim() : DEFAULT_BUYER_NAME;
        String email = hasText(req.getBuyerEmail()) ? req.getBuyerEmail().trim() : DEFAULT_EMAIL;
        String phone = hasText(req.getBuyerPhone())
                ? phoneNormalizer.normalize(req.getBuyerPhone()) : DEFAULT_PHONE;

        Order order = Order.builder()
                .orderNo(codeGenerator.generateOrderNo())
                .productAmount(supply)
                .discountAmount(BigDecimal.ZERO)
                .shippingAmount(BigDecimal.ZERO)
                .taxAmount(tax)
                .totalAmount(gross)
                .ordererName(name)
                .ordererEmail(email)
                .ordererPhone(phone)
                .build();
        order.applyOrdererIndex(piiIndex.ofEmail(email), piiIndex.ofPhone(phone), piiIndex.last4Of(phone));
        order.markOnSite(UUID.randomUUID().toString().replace("-", ""));
        orderRepository.save(order);

        OrderItem item = OrderItem.builder()
                .order(order)
                .artist(artist)
                .skuCodeSnapshot(ON_SITE)
                .artistCodeSnapshot(artist.getArtistCode())
                .skuNameSnapshot(req.getItemName().trim())
                .artistNameSnapshot(artist.getName())
                .quantity(1)
                .unitPrice(supply)
                .taxAmount(tax)
                .lineTotalAmount(gross)
                .build();
        orderItemRepository.save(item);
        order.getOrderItems().add(item);

        orderShipmentRepository.save(OrderShipment.builder()
                .order(order)
                .recipientName(name)
                .recipientPhone(phone)
                .zipCode("00000")
                .address1("현장 수령")
                .deliveryRequest(hasText(req.getMemo()) ? req.getMemo().trim() : null)
                .build());

        log.info("On-site order created: orderNo={}, artist={}, total={}",
                order.getOrderNo(), artist.getArtistCode(), gross);
        return OnSitePaymentDto.Response.from(order);
    }

    public OnSitePaymentDto.PublicResponse getByToken(String payToken) {
        Order order = orderRepository.findByPayToken(payToken)
                .filter(Order::isOnSite)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return OnSitePaymentDto.PublicResponse.from(order);
    }

    public Optional<String> payTokenOf(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return Optional.empty();
        return orderRepository.findByOrderNo(orderNo)
                .filter(Order::isOnSite)
                .map(Order::getPayToken);
    }

    public List<OnSitePaymentDto.Response> getRecent() {
        return orderRepository.findTop50ByOrderChannelOrderByCreatedAtDesc(ON_SITE).stream()
                .map(OnSitePaymentDto.Response::from)
                .toList();
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}