package com.koala.koalaback.infra.delivery;

public interface DeliveryTracker {
    Status track(String carrierCode, String trackingNo);

    enum Status {
        DELIVERED,

        IN_TRANSIT,

        UNKNOWN
    }
}
