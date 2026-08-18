package com.koala.koalaback.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class PaymentEventPayload {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PaymentEventPayload() {
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;

        try {
            MAPPER.readTree(raw);
            return raw;
        } catch (Exception notJson) {
            try {
                ObjectNode wrapped = MAPPER.createObjectNode();
                wrapped.put("message", raw);
                return MAPPER.writeValueAsString(wrapped);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
