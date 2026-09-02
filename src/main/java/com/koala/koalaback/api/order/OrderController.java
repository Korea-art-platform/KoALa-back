package com.koala.koalaback.api.order;

import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.order.service.OrderService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderDetailResponse> createOrder(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OrderDto.CreateRequest req) {
        return ApiResponse.ok(orderService.createOrder(userId, req));
    }

    /**
     * 비회원 주문.
     *
     * 회원 주문과 같은 서비스를 탄다 — 금액·재고·부가세·결제가 갈라지면
     * 한쪽만 고쳐지는 일이 생긴다. userId 가 없다는 것만 다르다.
     */
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderDetailResponse> createGuestOrder(
            @Valid @RequestBody OrderDto.CreateRequest req) {
        return ApiResponse.ok(orderService.createOrder(null, req));
    }

    /**
     * 비회원 주문 조회. 주문번호와 주문할 때 적은 휴대폰번호가 맞아야 한다.
     *
     * 주문번호가 URL 에 남지 않도록 POST 로 받는다. 브라우저 기록·중계 서버
     * 로그에 남으면 그것만으로 남의 주문이 열린다.
     */
    @PostMapping("/guest/lookup")
    public ApiResponse<OrderDto.OrderDetailResponse> lookupGuestOrder(
            @Valid @RequestBody OrderDto.GuestLookupRequest req) {
        return ApiResponse.ok(orderService.getGuestOrder(req.getOrderNo(), req.getPhone()));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>> getMyOrders(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(orderService.getMyOrders(userId, pageable));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDto.OrderDetailResponse> getMyOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getMyOrder(userId, orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDto.OrderDetailResponse> cancelOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.cancelOrder(userId, orderNo));
    }
}
