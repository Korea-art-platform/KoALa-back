package com.koala.koalaback.global.util;

import com.koala.koalaback.global.exception.BusinessException;
import com.koala.koalaback.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PhoneNormalizer {
    private static final String E164_REGEX = "^\\+[1-9][0-9]{6,14}$";

    public String normalize(String phone) {
        if (phone == null || phone.isBlank()) return null;

        String digits = phone.replaceAll("[^0-9+]", "");

        if (digits.startsWith("+")) {
            validate(digits);
            return digits;
        }

        if (digits.startsWith("0")) {
            digits = "+82" + digits.substring(1);
        }

        validate(digits);
        return digits;
    }

    public void validate(String phone) {
        if (phone == null || !phone.matches(E164_REGEX)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "올바른 전화번호 형식이 아닙니다. (+821012345678)");
        }
    }
}
