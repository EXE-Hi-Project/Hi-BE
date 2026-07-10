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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final PayOS payOS;
    private final RealtimeEventService realtimeEventService;
    private final VoucherOrderService voucherOrderService;
    private final PlanPricingService planPricingService;

    @Value("${app.client-url}")
    private String clientUrl;

    @Value("${app.payment.return-url.allowed-origins:${app.client-url}}")
    private String allowedReturnOrigins;

    public PaymentService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            PayOS payOS,
            RealtimeEventService realtimeEventService,
            VoucherOrderService voucherOrderService,
            PlanPricingService planPricingService) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.payOS = payOS;
        this.realtimeEventService = realtimeEventService;
        this.voucherOrderService = voucherOrderService;
        this.planPricingService = planPricingService;
    }

    public CheckoutSessionResult createCheckoutSession(User user, String priceId, String originUrl) throws Exception {
        // Block double payment: if user already has an active subscription
        if (user.getSubscription() != null && "active".equalsIgnoreCase(user.getSubscription().getStatus())) {
            if (user.getSubscription().getCurrentPeriodEnd() != null && 
                    user.getSubscription().getCurrentPeriodEnd().isAfter(Instant.now())) {
                throw new IllegalArgumentException("Bạn đang sử dụng Hi Pro hoặc Hi Max. Không thể tạo phiên thanh toán mới.");
            }
        }

        PlanPricingService.ResolvedPlan resolved = planPricingService.resolvePlan(priceId);
        PlanPricingService.PlanPrice selectedPlan = resolved.plan();
        long amount = selectedPlan.currentPrice();
        String planName = selectedPlan.code();

        // PayOS orderCode must be a Long integer.
        // We combine the current epoch seconds with a random 4-digit code.
        long orderCode = (System.currentTimeMillis() / 1000) * 10000 + (long) (Math.random() * 10000);

        if (amount == 0) {
            ensureSubscription(user);
            user.getSubscription().setPayosOrderCode(orderCode);
            user.getSubscription().setPlan(planName);

            Transaction transaction = newTransaction(user, selectedPlan, resolved.campaignId(), orderCode, "completed");

            Instant currentPeriodEnd = activateSubscription(user, transaction, amount, "payment.free_plan_activated");
            log.info("Activated free sale plan for user: {}, orderCode: {}", user.getEmail(), orderCode);
            return CheckoutSessionResult.activated(user.getSubscription(), currentPeriodEnd, planName, amount);
        }

        String baseUrl = resolveReturnBaseUrl(originUrl);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description("PREMIUM_YEARLY".equals(planName) ? "Hi Max" : "Hi Pro")
                .returnUrl(baseUrl + "/payment/success?orderCode=" + orderCode)
                .cancelUrl(baseUrl + "/payment/cancel")
                .build();

        CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);

        // Update user state with the pending transaction code
        ensureSubscription(user);
        user.getSubscription().setPayosOrderCode(orderCode);
        user.getSubscription().setPlan(planName);
        user.getSubscription().setStatus("pending");
        user.getSubscription().setCancelAtPeriodEnd(false);
        userRepository.save(user);

        // Create transaction log in history
        Transaction transaction = newTransaction(user, selectedPlan, resolved.campaignId(), orderCode, "pending");
        transactionRepository.save(transaction);

        log.info("Created PayOS payment link for user: {}, orderCode: {}, url: {}", user.getEmail(), orderCode, response.getCheckoutUrl());
        return CheckoutSessionResult.checkout(response.getCheckoutUrl());
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

            if (voucherOrderService.handlePaymentWebhook(orderCode, (long) data.getAmount())) {
                log.info("Processed PayOS webhook as voucher order. OrderCode: {}", orderCode);
                return;
            }

            Optional<User> userOpt = userRepository.findByPayosOrderCode(orderCode);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Transaction transaction = transactionRepository.findByOrderCode(orderCode)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch chờ xử lý"));
                long paidAmount = transaction.getPaidAmount() != null ? transaction.getPaidAmount() : transaction.getAmount();
                if (paidAmount != data.getAmount()) {
                    throw new IllegalArgumentException("Số tiền webhook không khớp giao dịch");
                }
                if ("completed".equalsIgnoreCase(transaction.getStatus())) {
                    return;
                }
                if (!"pending".equalsIgnoreCase(transaction.getStatus())) {
                    throw new IllegalArgumentException("Giao dịch không còn ở trạng thái chờ");
                }

                Instant currentPeriodEnd = activateSubscription(user, transaction, data.getAmount(), "payment.completed");
                log.info("Successfully upgraded user {} to paid Hi plan. Expiration: {}", user.getEmail(), currentPeriodEnd);
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
            realtimeEventService.sendSubscription(user.getId(), "subscription.updated", Map.of(
                    "subscription", user.getSubscription()
            ));
            Map<String, Object> canceledData = new java.util.LinkedHashMap<>();
            canceledData.put("cancelAtPeriodEnd", user.getSubscription().getCancelAtPeriodEnd());
            canceledData.put("activeUntil", activeUntil);
            realtimeEventService.sendSubscription(user.getId(), "payment.canceled", canceledData);
            realtimeEventService.sendAdminOverviewUpdated("admin.overview.updated", Map.of(
                    "reason", "payment.canceled",
                    "userId", user.getId()
            ));
            log.info("Canceled auto-renewal/active subscription status for user: {}", user.getEmail());
        }
    }

    public List<Transaction> getPaymentHistory(User user) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    private void ensureSubscription(User user) {
        if (user.getSubscription() == null) {
            user.setSubscription(new User.SubscriptionInfo());
        }
    }

    private Transaction newTransaction(User user,
                                       PlanPricingService.PlanPrice selectedPlan,
                                       String campaignId,
                                       long orderCode,
                                       String status) {
        Transaction transaction = new Transaction();
        transaction.setUserId(user.getId());
        transaction.setUserEmail(user.getEmail());
        transaction.setOrderCode(orderCode);
        transaction.setAmount(selectedPlan.currentPrice());
        transaction.setBaseAmount(selectedPlan.basePrice());
        transaction.setPaidAmount(selectedPlan.currentPrice());
        transaction.setCampaignId(campaignId);
        transaction.setPlanDisplayName(selectedPlan.name());
        transaction.setPlan(selectedPlan.code());
        transaction.setStatus(status);
        transaction.setDescription(selectedPlan.name());
        return transaction;
    }

    private Instant activateSubscription(User user, Transaction transaction, long amount, String eventType) {
        ensureSubscription(user);
        user.getSubscription().setStatus("active");
        String plan = user.getSubscription().getPlan();
        int days = plan != null && plan.toLowerCase().contains("yearly") ? 365 : 30;
        Instant currentPeriodEnd = Instant.now().plus(java.time.Duration.ofDays(days));
        user.getSubscription().setCurrentPeriodEnd(currentPeriodEnd);
        user.getSubscription().setCancelAtPeriodEnd(false);
        userRepository.save(user);

        transaction.setStatus("completed");
        transactionRepository.save(transaction);

        realtimeEventService.sendSubscription(user.getId(), "subscription.updated", Map.of(
                "subscription", user.getSubscription()
        ));
        realtimeEventService.sendSubscription(user.getId(), eventType, Map.of(
                "orderCode", transaction.getOrderCode(),
                "plan", plan,
                "amount", amount,
                "currentPeriodEnd", currentPeriodEnd
        ));
        realtimeEventService.sendAdminOverviewUpdated("admin.overview.updated", Map.of(
                "reason", eventType,
                "userId", user.getId()
        ));
        return currentPeriodEnd;
    }

    public record CheckoutSessionResult(
            boolean activated,
            String checkoutUrl,
            User.SubscriptionInfo subscription,
            Instant currentPeriodEnd,
            String plan,
            long amount
    ) {
        static CheckoutSessionResult checkout(String checkoutUrl) {
            return new CheckoutSessionResult(false, checkoutUrl, null, null, null, 0);
        }

        static CheckoutSessionResult activated(User.SubscriptionInfo subscription,
                                               Instant currentPeriodEnd,
                                               String plan,
                                               long amount) {
            return new CheckoutSessionResult(true, null, subscription, currentPeriodEnd, plan, amount);
        }

        public Map<String, Object> toResponseData() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("activated", activated);
            if (checkoutUrl != null) data.put("checkoutUrl", checkoutUrl);
            if (subscription != null) data.put("subscription", subscription);
            if (currentPeriodEnd != null) data.put("currentPeriodEnd", currentPeriodEnd);
            if (plan != null) data.put("plan", plan);
            data.put("amount", amount);
            return data;
        }
    }
}

