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

    /**
     * 주문 목록 조회 — 회원ID / 주문자명 / 전화번호 검색 지원
     * 검색 파라미터를 모두 생략하면 전체 목록 반환
     */
    @GetMapping
    public ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>> getOrders(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // 검색 조건이 하나라도 있으면 검색, 없으면 전체 조회
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
        // getOrderEntityByNo → from() 변환을 service 트랜잭션 안에서 수행
        return ApiResponse.ok(orderService.getAdminOrderDetail(orderNo));
    }

    /** 관리자 강제 취소 — 취소 사유 필수, 부분환불 금액 선택 */
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

    /**
     * 운송장 등록 화면의 택배사 목록.
     *
     * <p>화면에 목록을 박아 두지 않고 서버에서 내려주는 이유는, 이 코드가 <b>조회 API 가 쓰는
     * 값</b>이기 때문이다. 양쪽에 따로 적어 두면 한쪽만 고쳤을 때 운송장은 등록되는데
     * 자동 추적만 조용히 멈춘다.
     */
    @GetMapping("/carriers")
    public ApiResponse<List<CarrierResponse>> getCarriers() {
        return ApiResponse.ok(Arrays.stream(Carrier.values())
                .map(c -> new CarrierResponse(c.getCode(), c.getDisplayName()))
                .toList());
    }

    public record CarrierResponse(String code, String name) {}
}
