package com.koala.koalaback.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        String code,
        String message,
        String path,
        OffsetDateTime timestamp,
        List<FieldError> fieldErrors
) {
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return new ErrorResponse(
                false,
                errorCode.getCode(),
                errorCode.getMessage(),
                path,
                OffsetDateTime.now(),
                null
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, String overrideMessage) {
        return new ErrorResponse(
                false,
                errorCode.getCode(),
                overrideMessage,
                path,
                OffsetDateTime.now(),
                null
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                false,
                errorCode.getCode(),
                errorCode.getMessage(),
                path,
                OffsetDateTime.now(),
                fieldErrors
        );
    }

    public record FieldError(
            String field,
            Object rejectedValue,
            String reason
    ) {
    }
}
