package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentService {

    private final UserRepository userRepository;

    @Value("${app.stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${app.stripe.webhook-secret}")
    private String stripeWebhookSecret;

    @Value("${app.client-url}")
    private String clientUrl;

    public PaymentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    public String createCheckoutSession(User user, String priceId) throws StripeException {
        String customerId = user.getSubscription().getStripeCustomerId();
        if (customerId == null || customerId.isEmpty()) {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .setName(user.getName())
                    .putMetadata("userId", user.getId())
                    .build();
            Customer customer = Customer.create(customerParams);
            customerId = customer.getId();
            
            user.getSubscription().setStripeCustomerId(customerId);
            userRepository.save(user);
        }

        SessionCreateParams sessionParams = SessionCreateParams.builder()
                .setCustomer(customerId)
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(clientUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(clientUrl + "/payment/cancel")
                .putMetadata("userId", user.getId())
                .build();

        Session session = Session.create(sessionParams);
        return session.getUrl();
    }

    public void handleWebhook(String payload, String sigHeader) throws Exception {
        Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);

        switch (event.getType()) {
            case "checkout.session.completed":
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) {
                    String customerId = session.getCustomer();
                    String subscriptionId = session.getSubscription();
                    Subscription stripeSub = Subscription.retrieve(subscriptionId);
                    
                    Optional<User> userOpt = userRepository.findByStripeCustomerId(customerId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.getSubscription().setStripeSubscriptionId(subscriptionId);
                        user.getSubscription().setPlan("premium");
                        user.getSubscription().setStatus(stripeSub.getStatus());
                        user.getSubscription().setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()));
                        userRepository.save(user);
                    }
                }
                break;

            case "customer.subscription.deleted":
                Subscription subDeleted = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (subDeleted != null) {
                    String subscriptionId = subDeleted.getId();
                    Optional<User> userOpt = userRepository.findByStripeSubscriptionId(subscriptionId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.getSubscription().setPlan("free");
                        user.getSubscription().setStatus("canceled");
                        userRepository.save(user);
                    }
                }
                break;

            case "invoice.payment_failed":
                com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                if (invoice != null) {
                    String customerId = invoice.getCustomer();
                    Optional<User> userOpt = userRepository.findByStripeCustomerId(customerId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.getSubscription().setStatus("past_due");
                        userRepository.save(user);
                    }
                }
                break;
        }
    }

    public void cancelSubscription(User user) throws StripeException {
        String subId = user.getSubscription().getStripeSubscriptionId();
        if (subId == null || subId.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy subscription để hủy");
        }

        Subscription sub = Subscription.retrieve(subId);
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .setCancelAtPeriodEnd(true)
                .build();
        sub.update(params);
    }
}
