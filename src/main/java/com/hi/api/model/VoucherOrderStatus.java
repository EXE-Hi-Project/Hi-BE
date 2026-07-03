package com.hi.api.model;

public enum VoucherOrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    ISSUING,
    ISSUED,
    DELIVERED,
    PAYMENT_EXPIRED,
    ISSUE_RETRY,
    REFUND_REQUIRED,
    REFUNDED,
    CANCELED
}
