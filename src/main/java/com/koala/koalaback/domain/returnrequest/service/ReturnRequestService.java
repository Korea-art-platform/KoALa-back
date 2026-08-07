package com.koala.koalaback.domain.returnrequest.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import com.koala.koalaback.domain.returnrequest.repository.ReturnRequestRepository;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.domain.user.entity.User;
import com.koala.koalaback.domain.user.service.UserService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import com.koala.koalaback.global.response.PageResponse;
import com.koala.koalaback.global.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnRequestService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final UserService userService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final StockService stockService;
    private final CodeGenerator codeGenerator;
    /** 반품 처리의 DB 단계 — 자기호출을 피하려고 별도 빈으로 분리했다 */
    private final ReturnRequestTransactionService returnRequestTransactionService;

    /** 사용자 — 반품/교환 신청 */
    @Transactional
    public ReturnRequestDto.ReturnResponse createReturnRequest(Long userId, ReturnRequestDto.CreateRequest req) {
        Order order = orderRepository.findByOrderNoAndUserId(req.getOrderNo(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 배송완료 상태만 반품 신청 가능
        if (!"DELIVERED".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.RETURN_REQUEST_NOT_ALLOWED);
        }

        // 이미 진행 중인 반품 요청이 있는지 확인 (REJECTED 제외)
        boolean alreadyExists = returnRequestRepository
                .existsByOrderIdAndStatusNot(order.getId(), "REJECTED");
        if (alreadyExists) {
            throw new BusinessException(ErrorCode.RETURN_REQUEST_ALREADY_EXISTS);
        }

        User user = userService.getUserById(userId);

        ReturnRequest returnRequest = ReturnRequest.builder()
                .returnNo(codeGenerator.generateReturnNo())
                .order(order)
                .user(user)
                .returnType(req.getReturnType())
                .reason(req.getReason())
                .reasonDetail(req.getReasonDetail())
                .build();

        returnRequestRepository.save(returnRequest);
        log.info("Return request created: returnNo={}, orderNo={}, userId={}",
                returnRequest.getReturnNo(), req.getOrderNo(), userId);

        return ReturnRequestDto.ReturnResponse.from(returnRequest);
    }

    /** 사용자 — 내 반품 목록 */
    public List<ReturnRequestDto.ReturnResponse> getMyReturnRequests(Long userId) {
        return returnRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ReturnRequestDto.ReturnResponse::from)
                .toList();
    }

    /** 사용자 — 특정 주문의 반품 상태 확인 */
    public ReturnRequestDto.ReturnResponse getMyReturnByOrderNo(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return returnRequestRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(ReturnRequestDto.ReturnResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_REQUEST_NOT_FOUND));
    }

    /** 관리자 — 전체 반품 목록 */
    public PageResponse<ReturnRequestDto.ReturnResponse> getAdminReturnRequests(String status, Pageable pageable) {
        return PageResponse.of(
                returnRequestRepository.findByStatusFilter(status, pageable)
                        .map(ReturnRequestDto.ReturnResponse::from)
        );
    }

    /** 관리자 — 반품 상세 */
    public ReturnRequestDto.ReturnResponse getAdminReturnDetail(String returnNo) {
        return ReturnRequestDto.ReturnResponse.from(getByReturnNo(returnNo));
    }

    /**
     * 관리자 — 승인 또는 거절 처리.
     *
     * <p>{@code NOT_SUPPORTED} 로 클래스 레벨의 읽기 트랜잭션을 무력화한다.
     * 이 메서드에 트랜잭션이 열려 있으면 아래 PG 환불 호출이 다시 트랜잭션 안에 갇힌다.
     * <pre>
     * ① applyDecision  [트랜잭션] 승인/거절 + 재고 복구 → 커밋
     * ② paymentService.cancel  [트랜잭션 밖] PG 환불 (best-effort)
     * </pre>
     *
     * <p>환불은 best-effort 다 — 실패해도 반품 승인은 유지하고 실패 이벤트만 남겨 수동 처리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReturnRequestDto.ReturnResponse processReturnRequest(String returnNo, ReturnRequestDto.AdminProcessRequest req) {
        // ① DB 단계
        ReturnRequestTransactionService.ReturnDecision decision =
                returnRequestTransactionService.applyDecision(returnNo, req);

        // ② 환불 — 트랜잭션 밖
        if (decision.needsRefund()) {
            try {
                paymentService.cancel(decision.refundPaymentNo(),
                        new PaymentDto.CancelRequest("반품 승인 환불", decision.refundAmount()));
                log.info("Return refund success: paymentNo={}, amount={}",
                        decision.refundPaymentNo(), decision.refundAmount());
            } catch (Exception e) {
                log.error("Return refund FAILED — manual action required: paymentNo={}, returnNo={}, error={}",
                        decision.refundPaymentNo(), returnNo, e.getMessage());
                paymentService.recordRefundFailure(decision.refundPaymentNo(),
                        "반품 승인 환불 실패 — 수동처리 필요: " + e.getMessage());
            }
        }

        return returnRequestTransactionService.getDetail(returnNo);
    }

    /** 관리자 — 완료 처리 (교환 완료 등) */
    @Transactional
    public ReturnRequestDto.ReturnResponse completeReturnRequest(String returnNo) {
        ReturnRequest returnRequest = getByReturnNo(returnNo);
        if (!"APPROVED".equals(returnRequest.getStatus())) {
            throw new BusinessException(ErrorCode.RETURN_REQUEST_NOT_ALLOWED);
        }
        // 교환(EXCHANGE) 완료 처리 시 재고 복구 — 반품은 APPROVE 시점에 이미 처리됨
        if ("EXCHANGE".equals(returnRequest.getReturnType())) {
            returnRequest.getOrder().getOrderItems().forEach(item -> {
                if (item.getSku() != null) {
                    stockService.restoreByReturn(item.getSku().getId(), item.getQuantity(), item.getId());
                }
            });
            log.info("Stock restored on exchange completion: returnNo={}", returnNo);
        }
        returnRequest.complete();
        log.info("Return completed: returnNo={}", returnNo);
        return ReturnRequestDto.ReturnResponse.from(returnRequest);
    }

    private ReturnRequest getByReturnNo(String returnNo) {
        return returnRequestRepository.findByReturnNo(returnNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_REQUEST_NOT_FOUND));
    }
}
