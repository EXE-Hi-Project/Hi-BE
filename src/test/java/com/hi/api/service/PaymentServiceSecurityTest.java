package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.TransactionRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentServiceSecurityTest {

    @Test
    void rejectsUntrustedCheckoutReturnOrigin() {
        PaymentService service = service();

        assertEquals(
                "https://hilover.space",
                ReflectionTestUtils.invokeMethod(service, "resolveReturnBaseUrl", "https://evil.example")
        );
    }

    @Test
    void acceptsExactAllowlistedCheckoutReturnOrigin() {
        PaymentService service = service();

        assertEquals(
                "https://www.hilover.space",
                ReflectionTestUtils.invokeMethod(service, "resolveReturnBaseUrl", "https://www.hilover.space/")
        );
    }

    @Test
    void cancelKeepsPremiumActiveUntilPaidPeriodEnds() {
        UserRepository userRepository = mock(UserRepository.class);
        PaymentService service = new PaymentService(
                userRepository,
                mock(TransactionRepository.class),
                null
        );
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        User.SubscriptionInfo subscription = new User.SubscriptionInfo();
        subscription.setPlan("PREMIUM_MONTHLY");
        subscription.setStatus("active");
        subscription.setCurrentPeriodEnd(Instant.now().plusSeconds(3_600));
        user.setSubscription(subscription);

        service.cancelSubscription(user);

        assertEquals("active", user.getSubscription().getStatus());
        assertTrue(user.getSubscription().getCancelAtPeriodEnd());
        verify(userRepository).save(user);
    }

    private PaymentService service() {
        PaymentService service = new PaymentService(
                mock(UserRepository.class),
                mock(TransactionRepository.class),
                null
        );
        ReflectionTestUtils.setField(service, "clientUrl", "https://hilover.space");
        ReflectionTestUtils.setField(
                service,
                "allowedReturnOrigins",
                "https://hilover.space,https://www.hilover.space"
        );
        return service;
    }
}
