package com.koala.koalaback.api.admin;

import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.DailyRevenueDto;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.response.ApiResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

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
