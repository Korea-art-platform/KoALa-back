package com.koala.koalaback.global.exception;

import com.koala.koalaback.infra.slack.ServerErrorAlerter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** 500 만 알린다. 4xx 는 대부분 잘못된 요청이라 알림 가치가 없다 */
    private final ServerErrorAlerter serverErrorAlerter;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), e.getMessage()));
    }

    // @Valid Body 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .toList();

        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), fieldErrors));
    }

    // PathVariable/RequestParam 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e, HttpServletRequest req) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), e.getMessage()));
    }

    // DB 제약 조건 위반 (Check constraint, Unique constraint 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest req) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String userMessage;

        if (msg.contains("ck_skus_prices")) {
            userMessage = "가격 오류: 판매가는 정가보다 클 수 없습니다. (판매가 ≤ 정가)";
        } else if (msg.contains("Duplicate entry") || msg.contains("unique") || msg.contains("UNIQUE")) {
            userMessage = "이미 사용 중인 값입니다. (슬러그 또는 코드 중복)";
        } else {
            userMessage = "데이터 저장 중 오류가 발생했습니다. 입력값을 확인해 주세요.";
        }

        // DB 제약조건명/스키마 정보는 서버 로그에만 기록 — 클라이언트 응답에는 절대 포함하지 않음
        // msg에 테이블명·컬럼명이 포함될 수 있으므로 WARN 레벨로만 서버 측 기록
        log.warn("[DataIntegrity] {} {} — type={}", req.getMethod(), req.getRequestURI(),
                e.getClass().getSimpleName());
        log.debug("[DataIntegrity] detail: {}", msg); // DEBUG 레벨: 운영에서는 출력 안 됨
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), userMessage));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e, HttpServletRequest req) {
        log.error("[500] {} {} — {}: {}", req.getMethod(), req.getRequestURI(),
                e.getClass().getSimpleName(), e.getMessage(), e);
        serverErrorAlerter.report(e, req.getMethod(), req.getRequestURI());
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI()));
    }

    private ErrorResponse.FieldError toFieldError(FieldError fe) {
        Object rejected = fe.getRejectedValue();
        return new ErrorResponse.FieldError(
                fe.getField(),
                rejected,
                fe.getDefaultMessage()
        );
    }
}