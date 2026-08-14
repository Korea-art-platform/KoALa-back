package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.order.dto.OrderDto;

import com.koala.koalaback.domain.order.service.OrderService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.infra.delivery.Carrier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>> getOrders(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        boolean hasSearch = userId != null
                || (name  != null && !name.isBlank())
                || (phone != null && !phone.isBlank());

        if (hasSearch) {
            return ApiResponse.ok(orderService.adminSearchOrders(userId, name, phone, pageable));
        }
        return ApiResponse.ok(orderService.getAdminOrders(pageable));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDto.OrderDetailResponse> getOrder(
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getAdminOrderDetail(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDto.OrderDetailResponse> adminCancelOrder(
            @PathVariable String orderNo,
            @Valid @RequestBody OrderDto.AdminCancelRequest req) {
        return ApiResponse.ok(orderService.adminCancelOrder(orderNo, req));
    }

    @PatchMapping("/{orderNo}/tracking")
    public ApiResponse<Void> registerTracking(
            @PathVariable String orderNo,
            @Valid @RequestBody OrderDto.RegisterTrackingRequest req) {
        orderService.registerTracking(orderNo, req);
        return ApiResponse.ok();
    }

    @PatchMapping("/{orderNo}/delivered")
    public ApiResponse<Void> markDelivered(@PathVariable String orderNo) {
        orderService.markDelivered(orderNo);
        return ApiResponse.ok();
    }

    @GetMapping("/carriers")
    public ApiResponse<List<CarrierResponse>> getCarriers() {
        return ApiResponse.ok(Arrays.stream(Carrier.values())
                .map(c -> new CarrierResponse(c.getCode(), c.getDisplayName()))
                .toList());
    }

    public record CarrierResponse(String code, String name) {}
}
