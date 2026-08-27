package com.hi.api.service;

import com.hi.api.model.Transaction;
import com.hi.api.model.User;
import com.hi.api.repository.TransactionRepository;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                null,
                mock(RealtimeEventService.class),
                mock(VoucherOrderService.class),
                mock(PlanPricingService.class)
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

    @Test
    void desktopCheckoutAlwaysReturnsToHttpsWebsite() {
        PaymentService service = service();

        assertEquals(
                "https://hilover.space",
                ReflectionTestUtils.invokeMethod(service, "resolveReturnBaseUrl", "hi-app://renderer")
        );
    }

    @Test
    void zeroAmountSaleActivatesSubscriptionWithoutPayos() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        RealtimeEventService realtimeEventService = mock(RealtimeEventService.class);
        PlanPricingService planPricingService = mock(PlanPricingService.class);
        PaymentService service = new PaymentService(
                userRepository,
                transactionRepository,
                null,
                realtimeEventService,
                mock(VoucherOrderService.class),
                planPricingService
        );
        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        PlanPricingService.PlanPrice plan = new PlanPricingService.PlanPrice(
                "monthly",
                "PREMIUM_MONTHLY",
                "Hi Pro",
                30,
                49_000L,
                0L,
                100
        );
        when(planPricingService.resolvePlan("monthly"))
                .thenReturn(new PlanPricingService.ResolvedPlan(plan, "sale-zero"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentService.CheckoutSessionResult result = service.createCheckoutSession(user, "monthly", "https://hilover.space");

        assertTrue(result.activated());
        assertEquals(0L, result.amount());
        assertEquals("PREMIUM_MONTHLY", user.getSubscription().getPlan());
        assertEquals("active", user.getSubscription().getStatus());
        assertNotNull(user.getSubscription().getCurrentPeriodEnd());
        verify(userRepository).save(user);
        verify(transactionRepository).save(any(Transaction.class));
        verify(realtimeEventService, times(2)).sendSubscription(any(), any(), any());
    }

    @Test
    void webhookResolvesUserFromTransactionEvenWhenSubscriptionContainsAnotherOrderCode() {
        UserRepository userRepository = mock(UserRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        PaymentService service = new PaymentService(
                userRepository,
                transactionRepository,
                null,
                mock(RealtimeEventService.class),
                mock(VoucherOrderService.class),
                mock(PlanPricingService.class)
        );
        Transaction paidTransaction = new Transaction();
        paidTransaction.setOrderCode(1001L);
        paidTransaction.setUserId("user-1");
        paidTransaction.setPlan("PREMIUM_MONTHLY");
        paidTransaction.setAmount(49_000L);
        paidTransaction.setPaidAmount(49_000L);
        paidTransaction.setStatus("pending");

        User user = new User();
        user.setId("user-1");
        user.setEmail("user@example.com");
        User.SubscriptionInfo subscription = new User.SubscriptionInfo();
        subscription.setPayosOrderCode(2002L);
        subscription.setStatus("pending");
        user.setSubscription(subscription);

        when(transactionRepository.findByOrderCode(1001L)).thenReturn(Optional.of(paidTransaction));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.handleSubscriptionPayment(1001L, 49_000L);

        assertEquals("active", user.getSubscription().getStatus());
        assertEquals(1001L, user.getSubscription().getPayosOrderCode());
        assertEquals("completed", paidTransaction.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void rejectsAnotherCheckoutWhilePaymentIsPending() {
        PaymentService service = service();
        User user = new User();
        User.SubscriptionInfo subscription = new User.SubscriptionInfo();
        subscription.setStatus("pending");
        user.setSubscription(subscription);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createCheckoutSession(user, "monthly", "https://hilover.space")
        );
    }

    private PaymentService service() {
        PaymentService service = new PaymentService(
                mock(UserRepository.class),
                mock(TransactionRepository.class),
                null,
                mock(RealtimeEventService.class),
                mock(VoucherOrderService.class),
                mock(PlanPricingService.class)
        );
        ReflectionTestUtils.setField(service, "clientUrl", "https://hilover.space");
        ReflectionTestUtils.setField(
                service,
                "allowedReturnOrigins",
                "https://hilover.space,https://www.hilover.space"
        );
        ReflectionTestUtils.setField(service, "desktopOrigin", "hi-app://renderer");
        return service;
    }
}
