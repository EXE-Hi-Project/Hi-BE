package com.hi.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "voucher_orders")
@CompoundIndexes({
        @CompoundIndex(name = "voucher_order_user_created_idx", def = "{ 'userId': 1, 'createdAt': -1 }"),
        @CompoundIndex(name = "voucher_order_status_created_idx", def = "{ 'status': 1, 'createdAt': 1 }")
})
public class VoucherOrder {

    @Id
    @JsonProperty("_id")
    private Long id;

    @Indexed
    private String userId;

    private String userEmail;

    @Indexed
    private Long productId;

    private String productName;

    private String productImageUrl;

    private String sourceName;

    private Integer quantity = 1;

    private Long unitAmount = 0L;

    private Long totalAmount = 0L;

    private String currency = "VND";

    @Indexed(unique = true)
    private Long orderCode;

    private String checkoutUrl;

    @Indexed(unique = true)
    private String transactionRefId;

    private VoucherOrderStatus status = VoucherOrderStatus.CREATED;

    private String deliveryEmail;

    private String voucherCode;

    private String voucherLink;

    private String gotItStatus;

    private String failureReason;

    private Instant paidAt;

    private Instant issuedAt;

    private Instant deliveredAt;

    private Instant refundedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
