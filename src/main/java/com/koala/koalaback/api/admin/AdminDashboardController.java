package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.DailyRevenueDto;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "어드민 · 대시보드", description = "요약 숫자")
@RestController
@RequestMapping("/admin/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Operation(summary = "요약 통계", description = """
            오늘·최근 7일·이번 달·전체의 주문 수, 이번 달과 전체 매출, 오늘 가입자와 전체
            회원 수, 그리고 결제 대기(PENDING_PAYMENT)·결제 완료(PAID) 주문 수를 한 번에
            돌려준다.

            기준 시각은 서버의 오늘 0시다. 최근 7일은 오늘을 포함해 6일 전 0시부터다.

            매출은 승인된 결제 금액의 합이다 — 취소·환불은 결제 상태로 걸러진다.
            """)
    @GetMapping("/stats")
    public ApiResponse<DashboardStats> getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart  = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return ApiResponse.ok(DashboardStats.builder()
                .todayOrders(orderRepository.countByCreatedAtAfter(todayStart))
                .weekOrders(orderRepository.countByCreatedAtAfter(weekStart))
                .monthOrders(orderRepository.countByCreatedAtAfter(monthStart))
                .totalOrders(orderRepository.count())
                .monthRevenue(paymentRepository.sumApprovedAmountAfter(monthStart))
                .totalRevenue(paymentRepository.sumTotalApprovedAmount())
                .todaySignups(userRepository.countByCreatedAtAfter(todayStart))
                .totalUsers(userRepository.count())
                .pendingOrders(orderRepository.countByOrderStatus("PENDING_PAYMENT"))
                .processingOrders(orderRepository.countByOrderStatus("PAID"))
                .build());
    }

    @Operation(summary = "일별 매출", description = """
            오늘을 포함해 최근 14일치다.
            """)
    @GetMapping("/daily-revenue")
    public ApiResponse<List<DailyRevenueDto>> getDailyRevenue() {
        LocalDateTime from = LocalDate.now().minusDays(13).atStartOfDay();
        List<DailyRevenueDto> result = paymentRepository.findDailyRevenueSince(from)
                .stream()
                .map(p -> new DailyRevenueDto(p.getDate(), p.getRevenue(), p.getOrderCount()))
                .toList();
        return ApiResponse.ok(result);
    }

    @Getter @Builder
    public static class DashboardStats {
        private long todayOrders;
        private long weekOrders;
        private long monthOrders;
        private long totalOrders;
        private BigDecimal monthRevenue;
        private BigDecimal totalRevenue;
        private long todaySignups;
        private long totalUsers;
        private long pendingOrders;
        private long processingOrders;
    }
}
