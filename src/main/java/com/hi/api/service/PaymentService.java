package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.model.Transaction;
import com.hi.api.repository.UserRepository;
import com.hi.api.repository.TransactionRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PayOS payOS;

    @Value("${app.client-url}")
    private String clientUrl;

    @Value("${app.payment.return-url.allowed-origins:${app.client-url}}")
    private String allowedReturnOrigins;

    public PaymentService(UserRepository userRepository, TransactionRepository transactionRepository, PayOS payOS) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.payOS = payOS;
    }

    public String createCheckoutSession(User user, String priceId, String originUrl) throws Exception {
        // Block double payment: if user already has an active subscription
        if (user.getSubscription() != null && "active".equalsIgnoreCase(user.getSubscription().getStatus())) {
            if (user.getSubscription().getCurrentPeriodEnd() != null && 
                    user.getSubscription().getCurrentPeriodEnd().isAfter(Instant.now())) {
                throw new IllegalArgumentException("Bạn đang sử dụng gói Premium hoạt động. Không thể tạo phiên thanh toán mới.");
            }
        }

        long amount = 49000L;
        String planName = "PREMIUM_MONTHLY";
        if ("yearly".equalsIgnoreCase(priceId) || priceId.contains("yearly") || priceId.contains("399000") || priceId.contains("premium_yearly")) {
            amount = 399000L;
            planName = "PREMIUM_YEARLY";
        }

        // PayOS orderCode must be a Long integer.
        // We combine the current epoch seconds with a random 4-digit code.
        long orderCode = (System.currentTimeMillis() / 1000) * 10000 + (long) (Math.random() * 10000);

        String baseUrl = resolveReturnBaseUrl(originUrl);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description("HiPremium" + ("PREMIUM_YEARLY".equals(planName) ? "Yearly" : "Monthly"))
                .returnUrl(baseUrl + "/payment/success?orderCode=" + orderCode)
                .cancelUrl(baseUrl + "/payment/cancel")
                .build();

        CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);

        // Update user state with the pending transaction code
        if (user.getSubscription() == null) {
            user.setSubscription(new User.SubscriptionInfo());
        }
        user.getSubscription().setPayosOrderCode(orderCode);
        user.getSubscription().setPlan(planName);
        user.getSubscription().setStatus("pending");
        user.getSubscription().setCancelAtPeriodEnd(false);
        userRepository.save(user);

        // Create transaction log in history
        Transaction transaction = new Transaction();
        transaction.setUserId(user.getId());
        transaction.setUserEmail(user.getEmail());
        transaction.setOrderCode(orderCode);
        transaction.setAmount(amount);
        transaction.setPlan(planName);
        transaction.setStatus("pending");
        transaction.setDescription("HiPremium " + ("PREMIUM_YEARLY".equals(planName) ? "Yearly" : "Monthly"));
        transactionRepository.save(transaction);

        log.info("Created PayOS payment link for user: {}, orderCode: {}, url: {}", user.getEmail(), orderCode, response.getCheckoutUrl());
        return response.getCheckoutUrl();
    }

    private String resolveReturnBaseUrl(String originUrl) {
        String fallback = normalizeOrigin(clientUrl);
        if (originUrl == null || originUrl.isBlank()) {
            return fallback;
        }
        String requested = normalizeOrigin(originUrl);
        boolean allowed = Arrays.stream(allowedReturnOrigins.split(","))
                .map(this::normalizeOrigin)
                .anyMatch(requested::equals);
        return allowed ? requested : fallback;
    }

    private String normalizeOrigin(String origin) {
        String value = origin == null ? "" : origin.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
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
                
                // Update User Subscription State
                user.getSubscription().setStatus("active");
                String plan = user.getSubscription().getPlan();
                int days = plan != null && plan.toLowerCase().contains("yearly") ? 365 : 30;
                Instant currentPeriodEnd = Instant.now().plus(java.time.Duration.ofDays(days));
                user.getSubscription().setCurrentPeriodEnd(currentPeriodEnd);
                user.getSubscription().setCancelAtPeriodEnd(false);
                userRepository.save(user);

                // Update Transaction Status
                Optional<Transaction> transOpt = transactionRepository.findByOrderCode(orderCode);
                if (transOpt.isPresent()) {
                    Transaction transaction = transOpt.get();
                    transaction.setStatus("completed");
                    transactionRepository.save(transaction);
                } else {
                    Transaction transaction = new Transaction();
                    transaction.setUserId(user.getId());
                    transaction.setUserEmail(user.getEmail());
                    transaction.setOrderCode(orderCode);
                    transaction.setAmount((long) data.getAmount());
                    transaction.setPlan(plan);
                    transaction.setStatus("completed");
                    transaction.setDescription(data.getDescription());
                    transactionRepository.save(transaction);
                }

                log.info("Successfully upgraded user {} to Premium. Expiration: {}", user.getEmail(), currentPeriodEnd);
            } else {
                log.warn("User not found for PayOS orderCode: {}", orderCode);
            }
        }
    }

    public void cancelSubscription(User user) {
        if (user.getSubscription() != null) {
            Instant activeUntil = user.getSubscription().getCurrentPeriodEnd();
            boolean stillActive = activeUntil != null && activeUntil.isAfter(Instant.now());
            user.getSubscription().setCancelAtPeriodEnd(stillActive);
            user.getSubscription().setStatus(stillActive ? "active" : "canceled");
            userRepository.save(user);
            
            // Mark corresponding transaction as canceled if it's pending
            if (user.getSubscription().getPayosOrderCode() != null) {
                Optional<Transaction> transOpt = transactionRepository.findByOrderCode(user.getSubscription().getPayosOrderCode());
                if (transOpt.isPresent() && "pending".equalsIgnoreCase(transOpt.get().getStatus())) {
                    Transaction transaction = transOpt.get();
                    transaction.setStatus("canceled");
                    transactionRepository.save(transaction);
                }
            }
            log.info("Canceled auto-renewal/active subscription status for user: {}", user.getEmail());
        }
    }

    public List<Transaction> getPaymentHistory(User user) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }
}

