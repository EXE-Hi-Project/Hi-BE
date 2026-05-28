package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final UserRepository userRepository;
    private final PayOS payOS;

    @Value("${app.client-url}")
    private String clientUrl;

    public PaymentService(UserRepository userRepository, PayOS payOS) {
        this.userRepository = userRepository;
        this.payOS = payOS;
    }

    public String createCheckoutSession(User user, String priceId) throws Exception {
        long amount = 49000L;
        String planName = "premium_monthly";
        if ("yearly".equalsIgnoreCase(priceId) || priceId.contains("yearly") || priceId.contains("399000") || priceId.contains("premium_yearly")) {
            amount = 399000L;
            planName = "premium_yearly";
        }

        // PayOS orderCode must be a Long integer.
        // We combine the current epoch seconds with a random 4-digit code.
        long orderCode = (System.currentTimeMillis() / 1000) * 10000 + (long) (Math.random() * 10000);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description("HiPremium" + ("yearly".equalsIgnoreCase(planName) ? "Yearly" : "Monthly"))
                .returnUrl(clientUrl + "/payment/success?orderCode=" + orderCode)
                .cancelUrl(clientUrl + "/payment/cancel")
                .build();

        CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);

        // Update user state with the pending transaction code
        if (user.getSubscription() == null) {
            user.setSubscription(new User.SubscriptionInfo());
        }
        user.getSubscription().setPayosOrderCode(orderCode);
        user.getSubscription().setPlan(planName);
        user.getSubscription().setStatus("pending");
        userRepository.save(user);

        log.info("Created PayOS payment link for user: {}, orderCode: {}, url: {}", user.getEmail(), orderCode, response.getCheckoutUrl());
        return response.getCheckoutUrl();
    }

    public void handleWebhook(Webhook webhook) throws Exception {
        // Automatically validates the webhook signature
        WebhookData data = payOS.webhooks().verify(webhook);

        if (data != null) {
            Long orderCode = data.getOrderCode();
            log.info("Received valid PayOS Webhook. OrderCode: {}, Amount: {}, Description: {}",
                    orderCode, data.getAmount(), data.getDescription());

            Optional<User> userOpt = userRepository.findByPayosOrderCode(orderCode);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.getSubscription().setStatus("active");

                // Auto calculate subscription period based on plan
                String plan = user.getSubscription().getPlan();
                int days = plan != null && plan.contains("yearly") ? 365 : 30;
                Instant currentPeriodEnd = Instant.now().plus(java.time.Duration.ofDays(days));
                user.getSubscription().setCurrentPeriodEnd(currentPeriodEnd);

                userRepository.save(user);
                log.info("Successfully upgraded user {} to Premium. Expiration: {}", user.getEmail(), currentPeriodEnd);
            } else {
                log.warn("User not found for PayOS orderCode: {}", orderCode);
            }
        }
    }

    public void cancelSubscription(User user) {
        if (user.getSubscription() != null) {
            user.getSubscription().setStatus("canceled");
            userRepository.save(user);
            log.info("Canceled auto-renewal/active subscription status for user: {}", user.getEmail());
        }
    }
}
