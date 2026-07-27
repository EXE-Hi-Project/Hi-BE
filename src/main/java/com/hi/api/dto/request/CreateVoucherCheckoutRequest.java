package com.hi.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateVoucherCheckoutRequest {
    public enum Client {
        WEB,
        MOBILE
    }

    @NotNull
    private Long productId;

    @Min(1)
    @Max(10)
    private Integer quantity = 1;

    private String deliveryEmail;

    @NotNull
    private Client client = Client.WEB;
}
