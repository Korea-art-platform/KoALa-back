package com.koala.koalaback.domain.returnrequest.event;

public record ReturnRequestedEvent(
        String returnNo,
        String orderNo,
        String returnType,
        String reason,
        String ordererName
) {}
