package com.koala.koalaback.domain.order.service;

import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.service.ReturnRequestService;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.repository.UserRepository;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("주문 소유권")
class OrderOwnershipTest extends IntegrationTestSupport {
    private static final String PREFIX = "OWNTEST";

    @Autowired private OrderService orderService;
    @Autowired private ReturnRequestService returnRequestService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM users WHERE user_code LIKE ?", PREFIX + "%");
    }

    private Long strangerId() {
        String unique = PREFIX + "-" + System.nanoTime();
        User stranger = userRepository.save(User.builder()
                .userCode(unique)
                .email(unique + "@test.local")
                .passwordHash("x")
                .name("남")
                .build());
        return stranger.getId();
    }

    @Test
    @DisplayName("남의 주문은 조회되지 않는다")
    void cannotReadSomeoneElsesOrder() {
        assertThatThrownBy(() -> orderService.getMyOrder(strangerId(), "ORD-NOT-MINE"))
                .as("없는 주문과 남의 주문을 구분해 알려주면 주문번호 존재 여부가 새어 나간다")
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("남의 주문은 취소되지 않는다")
    void cannotCancelSomeoneElsesOrder() {
        assertThatThrownBy(() -> orderService.cancelOrder(strangerId(), "ORD-NOT-MINE"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("남의 주문으로 반품을 신청할 수 없다")
    void cannotRequestReturnOnSomeoneElsesOrder() {
        ReturnRequestDto.CreateRequest req = new ReturnRequestDto.CreateRequest();
        req.setOrderNo("ORD-NOT-MINE");
        req.setReturnType("RETURN");
        req.setReason("SIMPLE_CHANGE");

        assertThatThrownBy(() -> returnRequestService.createReturnRequest(strangerId(), req))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("남의 주문의 반품 상태는 조회되지 않는다")
    void cannotReadSomeoneElsesReturn() {
        assertThatThrownBy(() ->
                returnRequestService.getMyReturnByOrderNo(strangerId(), "ORD-NOT-MINE"))
                .isInstanceOf(BusinessException.class);
    }
}
