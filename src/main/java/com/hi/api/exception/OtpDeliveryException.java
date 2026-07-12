package com.hi.api.exception;

public class OtpDeliveryException extends RuntimeException {

    private final String trackingId;

    public OtpDeliveryException(String trackingId) {
        super("Không thể gửi mã OTP lúc này. Vui lòng thử gửi lại sau.");
        this.trackingId = trackingId;
    }

    public String getTrackingId() {
        return trackingId;
    }
}
