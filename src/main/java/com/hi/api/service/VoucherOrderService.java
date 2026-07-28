package com.hi.api.service;

import com.hi.api.dto.request.CreateVoucherCheckoutRequest;
import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.model.User;
import com.hi.api.model.VoucherOrder;
import com.hi.api.model.VoucherOrderStatus;
import com.hi.api.repository.TransactionRepository;
import com.hi.api.repository.VoucherOrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class VoucherOrderService {

    private final VoucherOrderRepository voucherOrderRepository;
    private final TransactionRepository transactionRepository;
    private final AffiliateProductService affiliateProductService;
    private final SequenceService sequenceService;
    private final PayOS payOS;
    private final GotItBizClient gotItBizClient;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final RealtimeEventService realtimeEventService;

    @Value("${app.client-url}")
    private String clientUrl;

    @Value("${app.mobile-return-url:https://hilover.space}")
    private String mobileReturnUrl;

    @Value("${app.payment.return-url.allowed-origins:${app.client-url}}")
    private String allowedReturnOrigins;

    public VoucherOrderService(VoucherOrderRepository voucherOrderRepository,
                               TransactionRepository transactionRepository,
                               AffiliateProductService affiliateProductService,
                               SequenceService sequenceService,
                               PayOS payOS,
                               GotItBizClient gotItBizClient,
                               EmailService emailService,
                               NotificationService notificationService,
                               RealtimeEventService realtimeEventService) {
        this.voucherOrderRepository = voucherOrderRepository;
        this.transactionRepository = transactionRepository;
        this.affiliateProductService = affiliateProductService;
        this.sequenceService = sequenceService;
        this.payOS = payOS;
        this.gotItBizClient = gotItBizClient;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.realtimeEventService = realtimeEventService;
    }

    public Map<String, Object> createCheckout(User user, CreateVoucherCheckoutRequest req, String originUrl) throws Exception {
        AffiliateProduct product = affiliateProductService.getById(req.getProductId());
        if (!AffiliatePlatform.GOTIT.equals(product.getPlatform())) {
            throw new IllegalArgumentException("San pham nay khong phai voucher Got It");
        }
        if (product.getIsActive() == null || !product.getIsActive()) {
            throw new IllegalArgumentException("Voucher nay tam thoi khong kha dung");
        }

        int quantity = req.getQuantity() != null ? req.getQuantity() : 1;
        long unitAmount = positiveAmount(product.getPrice());
        long totalAmount = unitAmount * quantity;
        long orderId = sequenceService.next("voucher_orders");
        long orderCode = nextOrderCode();
        String transactionRefId = "HI-GOTIT-" + orderId + "-" + orderCode;
        String baseUrl = resolveReturnBaseUrl(originUrl, req.getClient());

        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(user.getId());
        order.setUserEmail(user.getEmail());
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setProductImageUrl(product.getImageUrl());
        order.setSourceName(product.getSourceName());
        order.setQuantity(quantity);
        order.setUnitAmount(unitAmount);
        order.setTotalAmount(totalAmount);
        order.setCurrency(product.getCurrency() != null ? product.getCurrency() : "VND");
        order.setOrderCode(orderCode);
        order.setTransactionRefId(transactionRefId);
        order.setDeliveryEmail(resolveDeliveryEmail(req.getDeliveryEmail(), user.getEmail()));
        order.setStatus(VoucherOrderStatus.PAYMENT_PENDING);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(totalAmount)
                .description("HiVoucher" + orderId)
                .returnUrl(baseUrl + "/products?voucherOrderId=" + orderId)
                .cancelUrl(baseUrl + "/products?voucherCanceled=1")
                .build();
        voucherOrderRepository.save(order);
        CreatePaymentLinkResponse response;
        try {
            response = payOS.paymentRequests().create(request);
        } catch (Exception exception) {
            order.setStatus(VoucherOrderStatus.CANCELED);
            order.setFailureReason("Khong tao duoc phien thanh toan voucher");
            voucherOrderRepository.save(order);
            throw exception;
        }
        order.setCheckoutUrl(response.getCheckoutUrl());
        VoucherOrder saved = voucherOrderRepository.save(order);

        return Map.of(
                "order", saved,
                "checkoutUrl", response.getCheckoutUrl()
        );
    }

    public List<VoucherOrder> getMyOrders(User user) {
        return voucherOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    boolean orderCodeExists(Long orderCode) {
        return voucherOrderRepository.existsByOrderCode(orderCode);
    }

    public VoucherOrder getOwnedOrder(User user, Long id) {
        VoucherOrder order = voucherOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay don voucher"));
        if (!order.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Ban khong co quyen xem voucher nay");
        }
        return order;
    }

    public VoucherOrder resendEmail(User user, Long id) {
        VoucherOrder order = getOwnedOrder(user, id);
        if (order.getVoucherCode() == null || order.getVoucherCode().isBlank()) {
            throw new IllegalArgumentException("Voucher chua san sang de gui lai email");
        }
        sendVoucherEmail(order);
        order.setDeliveredAt(Instant.now());
        order.setStatus(VoucherOrderStatus.DELIVERED);
        return voucherOrderRepository.save(order);
    }

    public boolean handlePaymentWebhook(Long orderCode, Long paidAmount) {
        VoucherOrder order = voucherOrderRepository.findByOrderCode(orderCode).orElse(null);
        if (order == null) {
            return false;
        }
        if (VoucherOrderStatus.ISSUED.equals(order.getStatus()) || VoucherOrderStatus.DELIVERED.equals(order.getStatus())) {
            return true;
        }
        if (!VoucherOrderStatus.PAYMENT_PENDING.equals(order.getStatus()) && !VoucherOrderStatus.ISSUE_RETRY.equals(order.getStatus())) {
            return true;
        }

        if (paidAmount != null && order.getTotalAmount() != null && !order.getTotalAmount().equals(paidAmount)) {
            order.setStatus(VoucherOrderStatus.REFUND_REQUIRED);
            order.setFailureReason("So tien PayOS khong khop voi don voucher");
            voucherOrderRepository.save(order);
            notifyRefundRequired(order);
            return true;
        }

        Instant claimedAt = Instant.now();
        long claimed = voucherOrderRepository.claimForIssuance(
                order.getId(),
                List.of(VoucherOrderStatus.PAYMENT_PENDING, VoucherOrderStatus.ISSUE_RETRY),
                claimedAt
        );
        if (claimed == 0) {
            return true;
        }
        order = voucherOrderRepository.findById(order.getId()).orElse(order);
        order.setStatus(VoucherOrderStatus.ISSUING);
        order.setPaidAt(claimedAt);
        order.setIssuingStartedAt(claimedAt);

        try {
            AffiliateProduct product = affiliateProductService.getById(order.getProductId());
            GotItBizClient.IssuedVoucher issuedVoucher = gotItBizClient.issueVoucher(order, product);
            order.setVoucherCode(issuedVoucher.code());
            order.setVoucherLink(issuedVoucher.link());
            order.setGotItStatus(issuedVoucher.status());
            order.setIssuedAt(Instant.now());
            order.setIssuingStartedAt(null);
            order.setStatus(VoucherOrderStatus.ISSUED);
            voucherOrderRepository.save(order);
        } catch (Exception ex) {
            order.setStatus(VoucherOrderStatus.REFUND_REQUIRED);
            order.setIssuingStartedAt(null);
            order.setFailureReason("Khong phat hanh duoc voucher tu nha cung cap");
            voucherOrderRepository.save(order);
            notifyRefundRequired(order);
            return true;
        }

        try {
            sendVoucherEmail(order);
            order.setDeliveredAt(Instant.now());
            order.setStatus(VoucherOrderStatus.DELIVERED);
            VoucherOrder delivered = voucherOrderRepository.save(order);
            notifyDelivered(delivered);
        } catch (Exception emailError) {
            // The voucher has already been issued. Keep it available in-app and
            // let the user retry delivery instead of incorrectly requesting a refund.
            order.setFailureReason("Voucher da phat hanh nhung email chua gui duoc");
            voucherOrderRepository.save(order);
        }
        return true;
    }

    @Scheduled(cron = "0 */10 * * * ?", zone = "Asia/Ho_Chi_Minh")
    public void reconcileStaleIssuingOrders() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        for (VoucherOrder order : voucherOrderRepository.findByStatusAndIssuingStartedAtBefore(
                VoucherOrderStatus.ISSUING,
                cutoff)) {
            order.setStatus(VoucherOrderStatus.REFUND_REQUIRED);
            order.setIssuingStartedAt(null);
            order.setFailureReason("Qua thoi gian doi phat hanh voucher; can doi soat thu cong");
            VoucherOrder saved = voucherOrderRepository.save(order);
            notifyRefundRequired(saved);
        }
    }

    private long positiveAmount(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Voucher chua co menh gia hop le");
        }
        return price.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }

    private long nextOrderCode() {
        for (int attempt = 0; attempt < 8; attempt++) {
            long orderCode = (System.currentTimeMillis() / 1000) * 10000 + (long) (Math.random() * 10000);
            if (!voucherOrderRepository.existsByOrderCode(orderCode) && transactionRepository.findByOrderCode(orderCode).isEmpty()) {
                return orderCode;
            }
        }
        throw new IllegalStateException("Khong tao duoc ma thanh toan voucher");
    }

    private String resolveDeliveryEmail(String requestedEmail, String fallbackEmail) {
        String email = requestedEmail != null && !requestedEmail.isBlank() ? requestedEmail.trim() : fallbackEmail;
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Email nhan voucher khong hop le");
        }
        return email;
    }

    String resolveReturnBaseUrl(String originUrl, CreateVoucherCheckoutRequest.Client client) {
        if (CreateVoucherCheckoutRequest.Client.MOBILE.equals(client)) {
            return normalizeOrigin(mobileReturnUrl);
        }
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

    private void sendVoucherEmail(VoucherOrder order) {
        String body = "Voucher cua ban da san sang.\n"
                + "San pham: " + order.getProductName() + "\n"
                + "Ma voucher: " + order.getVoucherCode() + "\n"
                + "Link su dung: " + order.getVoucherLink() + "\n"
                + "Vui long kiem tra dieu kien su dung cua doi tac truoc khi dung voucher.";
        emailService.sendOptionalEmail(order.getDeliveryEmail(), "Voucher Got It cua ban tu Hi", body);
    }

    private void notifyDelivered(VoucherOrder order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("voucherOrderId", order.getId());
        metadata.put("productName", order.getProductName());
        notificationService.createIdempotentNotification(
                order.getUserId(),
                "VOUCHER_DELIVERED",
                "Voucher Got It da san sang",
                "Voucher " + order.getProductName() + " da duoc them vao vi cua ban.",
                "/products",
                "voucher-delivered-" + order.getId(),
                metadata
        );
        realtimeEventService.sendToUser(order.getUserId(), "vouchers", "voucher.delivered", Map.of("order", order));
    }

    private void notifyRefundRequired(VoucherOrder order) {
        notificationService.createIdempotentNotification(
                order.getUserId(),
                "VOUCHER_REFUND_REQUIRED",
                "Don voucher can ho tro",
                "Thanh toan da ghi nhan nhung voucher chua phat hanh duoc. Admin se kiem tra va hoan tien neu can.",
                "/products",
                "voucher-refund-required-" + order.getId(),
                Map.of("voucherOrderId", order.getId())
        );
        realtimeEventService.sendAdminOverviewUpdated("admin.overview.updated", Map.of(
                "reason", "voucher.refund_required",
                "voucherOrderId", order.getId()
        ));
    }
}
