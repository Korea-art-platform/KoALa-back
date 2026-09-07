package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.order.dto.OrderDto;

import com.koala.koalaback.domain.order.service.OrderService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.infra.delivery.Carrier;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@Tag(name = "어드민 · 주문", description = "주문 조회·강제취소·송장")
@RestController
@RequestMapping("/admin/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final OrderService orderService;

    @Operation(summary = "주문 목록 (어드민)")
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

    @Operation(summary = "주문 상세 (어드민)", description = "고객용과 달리 userId 를 걸지 않고 주문번호만으로 연다.")
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDto.OrderDetailResponse> getOrder(
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getAdminOrderDetail(orderNo));
    }

    @Operation(summary = "주문 강제 취소", description = """
            고객이 취소할 수 없는 상태여도 관리자는 취소할 수 있다. 승인된 결제가 있으면
            환불한다.

            고객 취소와 마찬가지로 트랜잭션을 걸지 않는다(NOT_SUPPORTED) — PG 환불은
            외부 호출이라 그동안 DB 커넥션과 행 잠금을 붙들면 다른 요청까지 막힌다.
            """)
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDto.OrderDetailResponse> adminCancelOrder(
            @PathVariable String orderNo,
            @Valid @RequestBody OrderDto.AdminCancelRequest req) {
        return ApiResponse.ok(orderService.adminCancelOrder(orderNo, req));
    }

    @Operation(summary = "송장 등록", description = "택배사와 송장번호를 넣는다. 배송중으로 넘어간다.")
    @PatchMapping("/{orderNo}/tracking")
    public ApiResponse<Void> registerTracking(
            @PathVariable String orderNo,
            @Valid @RequestBody OrderDto.RegisterTrackingRequest req) {
        orderService.registerTracking(orderNo, req);
        return ApiResponse.ok();
    }

    @Operation(summary = "배송완료 처리", description = """
            배송완료가 되어야 고객이 반품·교환을 신청할 수 있다.
            """)
    @PatchMapping("/{orderNo}/delivered")
    public ApiResponse<Void> markDelivered(@PathVariable String orderNo) {
        orderService.markDelivered(orderNo);
        return ApiResponse.ok();
    }

    @Operation(summary = "택배사 목록", description = "송장 등록 화면의 선택지다.")
    @GetMapping("/carriers")
    public ApiResponse<List<CarrierResponse>> getCarriers() {
        return ApiResponse.ok(Arrays.stream(Carrier.values())
                .map(c -> new CarrierResponse(c.getCode(), c.getDisplayName()))
                .toList());
    }

    public record CarrierResponse(String code, String name) {}
}
