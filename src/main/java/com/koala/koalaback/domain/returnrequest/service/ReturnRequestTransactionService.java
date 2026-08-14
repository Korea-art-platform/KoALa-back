package com.koala.koalaback.domain.returnrequest.service;

import com.koala.koalaback.domain.payment.repository.PaymentRepository;
import com.koala.koalaback.domain.returnrequest.dto.ReturnRequestDto;
import com.koala.koalaback.domain.returnrequest.entity.ReturnRequest;
import com.koala.koalaback.domain.returnrequest.repository.ReturnRequestRepository;
import com.koala.koalaback.domain.sku.service.StockService;
import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnRequestTransactionService {
    private final ReturnRequestRepository returnRequestRepository;
    private final PaymentRepository paymentRepository;
    private final StockService stockService;

    public record ReturnDecision(String refundPaymentNo, BigDecimal refundAmount) {
        public boolean needsRefund() { return refundPaymentNo != null; }
    }

    @Transactional
    public ReturnDecision applyDecision(String returnNo, ReturnRequestDto.AdminProcessRequest req) {
        ReturnRequest returnRequest = returnRequestRepository.findByReturnNo(returnNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!"REQUESTED".equals(returnRequest.getStatus())) {
            throw new BusinessException(ErrorCode.RETURN_REQUEST_NOT_ALLOWED);
        }

        if ("REJECT".equals(req.getAction())) {
            returnRequest.reject(req.getAdminMemo());
            log.info("Return rejected: returnNo={}", returnNo);
            return new ReturnDecision(null, null);
        }

        if (!"APPROVE".equals(req.getAction())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        BigDecimal orderTotal = returnRequest.getOrder().getTotalAmount();
        BigDecimal refundAmt = req.getRefundAmount() != null ? req.getRefundAmount() : orderTotal;

        validateRefundAmount(refundAmt, orderTotal);

        returnRequest.approve(refundAmt, req.getAdminMemo());

        if ("RETURN".equals(returnRequest.getReturnType())) {
            returnRequest.getOrder().getOrderItems().stream()
                    .filter(item -> item.getSku() != null)
                    .sorted(Comparator.comparing(item -> item.getSku().getId()))
                    .forEach(item -> stockService.restoreByReturn(
                            item.getSku().getId(), item.getQuantity(), item.getId()));
            log.info("Stock restored on return approval: returnNo={}", returnNo);
        }

        log.info("Return approved: returnNo={}, refundAmt={}", returnNo, refundAmt);

        String refundPaymentNo = paymentRepository
                .findTopByOrderIdOrderByCreatedAtDesc(returnRequest.getOrder().getId())
                .filter(p -> "CAPTURED".equals(p.getStatus()))
                .map(p -> p.getPaymentNo())
                .orElse(null);

        return new ReturnDecision(refundPaymentNo, refundAmt);
    }

    private void validateRefundAmount(BigDecimal refundAmount, BigDecimal orderTotal) {
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "환불 금액은 1원 이상이어야 합니다.");
        }
        if (refundAmount.compareTo(orderTotal) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "환불 금액이 주문 금액(" + orderTotal.toPlainString() + "원)을 넘을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public ReturnRequestDto.ReturnResponse getDetail(String returnNo) {
        return ReturnRequestDto.ReturnResponse.from(
                returnRequestRepository.findByReturnNo(returnNo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }
}
