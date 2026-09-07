package com.koala.koalaback.api.order;

import com.koala.koalaback.domain.order.dto.OrderDto;
import com.koala.koalaback.domain.order.service.OrderService;
import com.koala.koalaback.global.response.ApiResponse;
import com.koala.koalaback.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "주문", description = "주문 생성·조회·취소. 비회원 주문 포함")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @Operation(summary = "주문 생성 (회원)", description = """
            장바구니에서 고른 것으로 주문하거나, cartItemIds 대신 directItem 을 보내
            장바구니를 거치지 않고 한 건만 주문한다. 물건을 어디서 가져오는지만 다르고
            금액·재고·부가세는 같은 길을 탄다.

            재고는 skuId 오름차순으로 하나씩 차감한다. 여러 건을 동시에 주문할 때
            락을 잡는 순서를 고정해 서로 엇갈려 기다리는 일을 막는다. SKU 행은
            PESSIMISTIC_WRITE 로 잠근다.

            금액은 요청이 아니라 DB 의 값으로 계산한다. 저장된 단가는 부가세를 뺀
            공급가액이고, 청구액은 여기에 10% 를 더한 값이다. 원작처럼 면세로 표시된
            분류에는 붙지 않는다. 배송비는 고객이 보는 금액(부가세 포함가) 기준으로
            5만원 이상이면 0원, 아니면 3,000원이며 그 3,000원 안에 든 세액만 장부에 남긴다.
            """)
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
    @Operation(summary = "주문 생성 (비회원)", description = """
            회원 주문과 같은 서비스를 탄다 — 금액·재고·부가세·결제가 갈라지면 한쪽만
            고쳐지는 일이 생긴다. userId 가 없다는 것만 다르다.

            비회원에게는 장바구니가 없어 directItem 이 반드시 있어야 한다. 없으면
            INVALID_INPUT 으로 거절한다.

            나중에 같은 이메일로 가입하면 이 주문이 그 계정에 붙는다. 가입 과정에서
            확인된 이메일이라 그 사람 것이 맞다.
            """)
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
    @Operation(summary = "비회원 주문 조회", description = """
            주문번호와 주문할 때 적은 휴대폰번호가 모두 맞아야 한다.

            GET 이 아니라 POST 다. 주문번호가 URL 에 남으면 브라우저 기록과 중계 서버
            로그만으로 남의 주문이 열린다.

            없는 주문과 번호가 틀린 경우를 같은 말로 돌려준다. 다르게 답하면 번호를
            넣어 보는 것만으로 어떤 주문이 있는지 알아낼 수 있다. 반복 시도는
            RateLimitFilter 가 로그인과 같은 급으로 막는다.
            """)
    @PostMapping("/guest/lookup")
    public ApiResponse<OrderDto.OrderDetailResponse> lookupGuestOrder(
            @Valid @RequestBody OrderDto.GuestLookupRequest req) {
        return ApiResponse.ok(orderService.getGuestOrder(req.getOrderNo(), req.getPhone()));
    }

    @Operation(summary = "내 주문 목록", description = "로그인한 사용자의 주문만 돌려준다. 기본 10건씩.")
    @GetMapping
    public ApiResponse<PageResponse<OrderDto.OrderSummaryResponse>> getMyOrders(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(orderService.getMyOrders(userId, pageable));
    }

    @Operation(summary = "내 주문 상세", description = """
            주문번호와 userId 를 함께 걸어 조회한다. 남의 주문번호를 알아도 열리지 않는다.
            """)
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDto.OrderDetailResponse> getMyOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getMyOrder(userId, orderNo));
    }

    @Operation(summary = "주문 취소", description = """
            취소 가능한 상태인지 먼저 확인하고, 승인(CAPTURED)된 결제가 있으면 환불한 뒤
            주문을 취소한다.

            이 메서드에는 트랜잭션을 걸지 않는다(NOT_SUPPORTED). PG 환불 호출은 외부
            네트워크라 몇 초가 걸릴 수 있는데, 그동안 DB 커넥션과 행 잠금을 붙들고 있으면
            다른 요청까지 막힌다. 대신 상태 확인 · 환불 · 취소 확정을 각각 짧은 트랜잭션으로
            끊는다.
            """)
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDto.OrderDetailResponse> cancelOrder(
            @AuthenticationPrincipal Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.cancelOrder(userId, orderNo));
    }
}
