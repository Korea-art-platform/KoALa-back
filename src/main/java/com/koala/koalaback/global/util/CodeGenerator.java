package com.koala.koalaback.global.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class CodeGenerator {
    private static final DateTimeFormatter ORDER_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String generateCode() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    public String generateOrderNo() {
        String time = LocalDateTime.now().format(ORDER_FMT);
        String suffix = generateCode().substring(0, 4);
        return "KL-" + time + "-" + suffix;
    }

    public String generatePaymentNo() {
        return "PAY-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    public String generateReviewCode() {
        return "REV-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }

    public String generateReturnNo() {
        return "RET-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}
