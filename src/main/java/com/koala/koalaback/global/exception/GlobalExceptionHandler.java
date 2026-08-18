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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final ServerErrorAlerter serverErrorAlerter;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), e.getMessage()));
    }

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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e, HttpServletRequest req) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), e.getMessage()));
    }

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

        log.warn("[DataIntegrity] {} {} — type={}", req.getMethod(), req.getRequestURI(),
                e.getClass().getSimpleName());
        log.debug("[DataIntegrity] detail: {}", msg);
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI(), userMessage));
    }

    /**
     * 존재하지 않는 경로 — 404 로 돌려준다.
     *
     * <p>이게 없으면 맨 아래 {@code Exception} 핸들러가 잡아 500 이 나간다. 두 가지가 잘못된다.
     * <ul>
     *   <li>없는 주소를 "서버 오류"라고 알려 준다 — 호출한 쪽이 원인을 잘못 찾는다</li>
     *   <li><b>500 슬랙 알림이 울린다.</b> 봇이 임의 주소를 훑기만 해도 알림이 쌓여,
     *       정작 중요한 주문·환불 알림이 묻힌다</li>
     * </ul>
     *
     * <p>실제로 나이스·페이플 복귀 경로를 시큐리티에서 열어 두고 컨트롤러는 꺼 둔 상태에서
     * 이 문제가 드러났다.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception e, HttpServletRequest req) {
        log.debug("[404] {} {}", req.getMethod(), req.getRequestURI());
        ErrorCode ec = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(ec.getStatus())
                .body(ErrorResponse.of(ec, req.getRequestURI()));
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
