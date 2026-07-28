package com.hi.api.service;

import com.hi.api.dto.request.CreateVoucherCheckoutRequest;
import com.hi.api.repository.TransactionRepository;
import com.hi.api.repository.VoucherOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class VoucherOrderReturnUrlTest {

    @Test
    void mobileCheckoutIgnoresClientOriginAndUsesServerAllowlist() {
        VoucherOrderService service = new VoucherOrderService(
                mock(VoucherOrderRepository.class),
                mock(TransactionRepository.class),
                mock(AffiliateProductService.class),
                mock(SequenceService.class),
                mock(PayOS.class),
                mock(GotItBizClient.class),
                mock(EmailService.class),
                mock(NotificationService.class),
                mock(RealtimeEventService.class)
        );
        ReflectionTestUtils.setField(service, "clientUrl", "https://hilover.space/");
        ReflectionTestUtils.setField(service, "mobileReturnUrl", "https://m.hilover.space/");
        ReflectionTestUtils.setField(
                service,
                "allowedReturnOrigins",
                "https://hilover.space,https://www.hilover.space"
        );

        assertEquals(
                "https://m.hilover.space",
                service.resolveReturnBaseUrl("https://attacker.example", CreateVoucherCheckoutRequest.Client.MOBILE)
        );
        assertEquals(
                "https://hilover.space",
                service.resolveReturnBaseUrl("https://attacker.example/", CreateVoucherCheckoutRequest.Client.WEB)
        );
        assertEquals(
                "https://www.hilover.space",
                service.resolveReturnBaseUrl("https://www.hilover.space/", CreateVoucherCheckoutRequest.Client.WEB)
        );
    }
}
