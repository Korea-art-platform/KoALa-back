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

/**
 * 반품 처리 흐름의 <b>DB 단계</b>만 담당한다. 외부 PG 호출은 들어오지 않는다.
 *
 * <p>{@link ReturnRequestService} 안에 두면 자기호출이라 프록시를 타지 않아
 * {@code @Transactional} 이 적용되지 않으므로 별도 빈으로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnRequestTransactionService {

    private final ReturnRequestRepository returnRequestRepository;
    private final PaymentRepository paymentRepository;
    private final StockService stockService;

    /**
     * 승인/거절 처리 결과 — 환불이 필요하면 결제번호와 금액이 채워진다.
     */
    public record ReturnDecision(String refundPaymentNo, BigDecimal refundAmount) {
        public boolean needsRefund() { return refundPaymentNo != null; }
    }

    /**
     * ① 반품 승인/거절 + 재고 복구까지 DB 작업을 마친다.
     *
     * @return 트랜잭션 밖에서 처리할 환불 정보
     */
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

        // 환불 금액: 명시하면 그 금액, 없으면 전액
        BigDecimal orderTotal = returnRequest.getOrder().getTotalAmount();
        BigDecimal refundAmt = req.getRefundAmount() != null ? req.getRefundAmount() : orderTotal;

        validateRefundAmount(refundAmt, orderTotal);

        returnRequest.approve(refundAmt, req.getAdminMemo());

        // 반품(RETURN) 승인 시 재고 복구 — 교환(EXCHANGE)은 교환 완료 처리 시점에 별도 처리
        if ("RETURN".equals(returnRequest.getReturnType())) {
            // restoreByReturn 이 SKU row 에 락을 걸므로 skuId 오름차순으로 잡는다(데드락 방지)
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

    /**
     * 환불 금액 검증.
     *
     * <p>이 값은 <b>관리자가 직접 입력</b>하고 그대로 PG 로 넘어가 돈이 나간다.
     * 결제 승인은 고객이 낸 금액과 대조할 수 있지만 환불은 대조할 상대가 주문 총액뿐이라,
     * 여기서 막지 않으면 숫자를 하나 잘못 눌러도 그대로 나간다.
     */
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
