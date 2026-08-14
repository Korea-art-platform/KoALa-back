package com.koala.koalaback.domain.returnrequest.service;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.payment.dto.PaymentDto;
import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.payment.service.PaymentService;
import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import com.koala.koalaback.domain.returnrequest.event.ReturnRequestedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    private final ReturnRequestTransactionService returnRequestTransactionService;

    @Transactional
    public ReturnRequestDto.ReturnResponse createReturnRequest(Long userId, ReturnRequestDto.CreateRequest req) {
        Order order = orderRepository.findByOrderNoAndUserId(req.getOrderNo(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!"DELIVERED".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.RETURN_REQUEST_NOT_ALLOWED);
        }

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

        eventPublisher.publishEvent(new ReturnRequestedEvent(
                returnRequest.getReturnNo(), order.getOrderNo(),
                req.getReturnType(), req.getReason(), order.getOrdererName()));

        return ReturnRequestDto.ReturnResponse.from(returnRequest);
    }

    public List<ReturnRequestDto.ReturnResponse> getMyReturnRequests(Long userId) {
        return returnRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ReturnRequestDto.ReturnResponse::from)
                .toList();
    }

    public ReturnRequestDto.ReturnResponse getMyReturnByOrderNo(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNoAndUserId(orderNo, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return returnRequestRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(ReturnRequestDto.ReturnResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RETURN_REQUEST_NOT_FOUND));
    }

    public PageResponse<ReturnRequestDto.ReturnResponse> getAdminReturnRequests(String status, Pageable pageable) {
        return PageResponse.of(
                returnRequestRepository.findByStatusFilter(status, pageable)
                        .map(ReturnRequestDto.ReturnResponse::from)
        );
    }

    public ReturnRequestDto.ReturnResponse getAdminReturnDetail(String returnNo) {
        return ReturnRequestDto.ReturnResponse.from(getByReturnNo(returnNo));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReturnRequestDto.ReturnResponse processReturnRequest(String returnNo, ReturnRequestDto.AdminProcessRequest req) {
        ReturnRequestTransactionService.ReturnDecision decision =
                returnRequestTransactionService.applyDecision(returnNo, req);

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

    @Transactional
    public ReturnRequestDto.ReturnResponse completeReturnRequest(String returnNo) {
        ReturnRequest returnRequest = getByReturnNo(returnNo);
        if (!"APPROVED".equals(returnRequest.getStatus())) {
            throw new BusinessException(ErrorCode.RETURN_REQUEST_NOT_ALLOWED);
        }

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
