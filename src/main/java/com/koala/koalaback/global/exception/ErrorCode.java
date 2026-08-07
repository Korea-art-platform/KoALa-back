package com.koala.koalaback.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 오류가 발생했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C004", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C005", "권한이 없습니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "C006", "이미 존재하는 리소스입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C007", "서버 내부 오류가 발생했습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "U003", "비밀번호가 올바르지 않습니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "U004", "정지된 계정입니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "U005", "비활성화된 계정입니다."),

    // Auth
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A001", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A003", "리프레시 토큰을 찾을 수 없습니다."),

    // Artist
    ARTIST_NOT_FOUND(HttpStatus.NOT_FOUND, "AR001", "아티스트를 찾을 수 없습니다."),
    ARTIST_SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "AR002", "이미 사용 중인 슬러그입니다."),

    // SKU
    SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "상품을 찾을 수 없습니다."),
    SKU_OUT_OF_STOCK(HttpStatus.CONFLICT, "S002", "재고가 부족합니다."),
    SKU_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "S003", "판매 중이 아닌 상품입니다."),
    INVALID_ANGLE_DEGREE(HttpStatus.BAD_REQUEST, "S004", "유효하지 않은 각도값입니다."),
    DUPLICATE_ANGLE_DEGREE(HttpStatus.BAD_REQUEST, "S005", "중복된 각도값이 있습니다."),
    FRAME_ANGLE_COUNT_MISMATCH(HttpStatus.BAD_REQUEST, "S006", "파일 수와 각도 배열 길이가 다릅니다."),
    //ARTWORK
    ARTWORK_NOT_FOUND(HttpStatus.NOT_FOUND, "AW001", "작품을 찾을 수 없습니다."),
    // Cart
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CA001", "장바구니를 찾을 수 없습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CA002", "장바구니 상품을 찾을 수 없습니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "주문을 찾을 수 없습니다."),
    ORDER_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "O002", "취소할 수 없는 주문 상태입니다."),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "O003", "이미 결제된 주문입니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "P002", "결제 금액이 일치하지 않습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "P003", "이미 처리된 결제입니다."),
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "P004", "결제 처리 중 오류가 발생했습니다."),
    /**
     * PG 응답을 받지 못해 승인 여부가 미확정 — 임의 재시도 금지, 재조회/웹훅으로 확정된다.
     *
     * <p>반드시 2xx 가 아니어야 한다. 프론트는 axios 로 승인을 호출하고 2xx 면 성공으로 보고
     * 결제 완료 화면으로 넘어가는데, 미확정을 202 로 주면 승인되지도 않은 결제가
     * 완료로 표시된다. 재시도해도 막히는 상태이므로 409 로 준다.
     */
    PAYMENT_IN_DOUBT(HttpStatus.CONFLICT, "P005",
            "결제 결과를 확인하는 중입니다. 잠시 후 주문 내역에서 상태를 확인해 주세요. 중복 결제를 막기 위해 재시도는 차단됩니다."),
    PAYMENT_IN_PROGRESS(HttpStatus.CONFLICT, "P006", "이미 진행 중인 결제입니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "리뷰를 찾을 수 없습니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "R002", "이미 리뷰를 작성했습니다."),
    REVIEW_NOT_ALLOWED(HttpStatus.FORBIDDEN, "R003", "리뷰 작성 권한이 없습니다."),

    // Admin
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "AD001", "관리자를 찾을 수 없습니다."),
    ADMIN_LOCKED(HttpStatus.FORBIDDEN, "AD002", "잠긴 관리자 계정입니다."),
    ADMIN_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AD003", "아이디 또는 비밀번호가 올바르지 않습니다."),

    // File
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F001", "파일 업로드에 실패했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "F002", "지원하지 않는 파일 형식입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "F003", "파일 크기가 허용 범위를 초과했습니다."),
    INVALID_FILE_SIGNATURE(HttpStatus.BAD_REQUEST, "F004", "파일 내용이 확장자와 일치하지 않습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U006", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "U007", "이메일 또는 비밀번호가 올바르지 않습니다."),
    //패스워드 리셋
    PASSWORD_RESET_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "PR001", "인증코드를 찾을 수 없습니다."),
    PASSWORD_RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "PR002", "인증 코드가 만료되었습니다."),
    SOCIAL_LOGIN_USER(HttpStatus.BAD_REQUEST, "PR003", "소셜 로그인 사용자는 비밀번호 찾기를 사용할 수 없습니다."),

    // Return Request
    RETURN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "RR001", "반품/교환 요청을 찾을 수 없습니다."),
    RETURN_REQUEST_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "RR002", "반품/교환 신청이 불가능한 상태입니다."),
    RETURN_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "RR003", "이미 반품/교환 요청이 접수된 주문입니다."),

    // Toss Login (앱인토스)
    TOSS_LOGIN_FAILED(HttpStatus.BAD_GATEWAY, "TL001", "토스 로그인 처리 중 오류가 발생했습니다."),
    TOSS_TOKEN_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY, "TL002", "토스 인가 코드 교환에 실패했습니다."),
    TOSS_USER_INFO_FAILED(HttpStatus.BAD_GATEWAY, "TL003", "토스 사용자 정보 조회에 실패했습니다.");
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    public HttpStatus getStatus(){
        return this.httpStatus;

    }
}