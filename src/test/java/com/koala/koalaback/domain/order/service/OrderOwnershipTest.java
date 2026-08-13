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

/**
 * 남의 주문에 손대지 못하는지 확인한다.
 *
 * <h3>왜 필요한가</h3>
 * <p>주문번호는 화면에 그대로 노출되고 형식도 규칙적이다. 값만 바꿔 호출하면 남의 주문이
 * 열리는지가 이 도메인에서 가장 아픈 실수다 — 이름·연락처·주소가 한 번에 나간다.
 *
 * <p>지금은 조회 쿼리 자체가 {@code (orderNo, userId)} 로 걸려 있어 막힌다. 다만 그건
 * "지금 그렇게 짜여 있다"는 사실일 뿐이라, 누가 {@code findByOrderNo} 로 바꾸면 조용히 뚫린다.
 * 그래서 동작으로 고정해 둔다.
 */
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

    /** 주문이 하나도 없는 사용자 — 남의 주문번호로 접근을 시도한다 */
    private Long strangerId() {
        // 이메일·코드가 UNIQUE 라 테스트마다 다른 값을 쓴다
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
