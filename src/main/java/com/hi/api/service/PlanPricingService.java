package com.hi.api.service;

import com.hi.api.dto.request.UpdatePlanPricingRequest;
import com.hi.api.dto.request.UpsertSaleCampaignRequest;
import com.hi.api.model.AdminAuditLog;
import com.hi.api.model.PlanPricingConfig;
import com.hi.api.model.SaleCampaign;
import com.hi.api.model.SaleCampaignStatus;
import com.hi.api.repository.AdminAuditLogRepository;
import com.hi.api.repository.PlanPricingConfigRepository;
import com.hi.api.repository.SaleCampaignRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class PlanPricingService {
    private static final EnumSet<SaleCampaignStatus> RUNNABLE_STATUSES =
            EnumSet.of(SaleCampaignStatus.SCHEDULED, SaleCampaignStatus.ACTIVE);

    private final PlanPricingConfigRepository pricingRepository;
    private final SaleCampaignRepository saleRepository;
    private final AdminAuditLogRepository auditLogRepository;

    public PlanPricingService(PlanPricingConfigRepository pricingRepository,
                              SaleCampaignRepository saleRepository,
                              AdminAuditLogRepository auditLogRepository) {
        this.pricingRepository = pricingRepository;
        this.saleRepository = saleRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Cacheable("plan_pricing")
    public PricingResponse getPublicPricing() {
        PlanPricingConfig config = getOrCreateConfig();
        Instant now = Instant.now();
        Optional<SaleCampaign> activeSale = saleRepository
                .findByStatusInOrderByStartsAtDesc(RUNNABLE_STATUSES)
                .stream()
                .filter(campaign -> !now.isBefore(campaign.getStartsAt()) && now.isBefore(campaign.getEndsAt()))
                .findFirst();

        SaleCampaign sale = activeSale.orElse(null);
        return new PricingResponse(
                plan("monthly", "PREMIUM_MONTHLY", "Hi Pro", 30,
                        config.getHiProBasePrice(), sale == null ? null : sale.getHiProSalePrice()),
                plan("yearly", "PREMIUM_YEARLY", "Hi Max", 365,
                        config.getHiMaxBasePrice(), sale == null ? null : sale.getHiMaxSalePrice()),
                sale == null ? null : toSaleView(sale, SaleCampaignStatus.ACTIVE)
        );
    }

    public ResolvedPlan resolvePlan(String requestedPlan) {
        String normalized = requestedPlan == null ? "" : requestedPlan.trim().toLowerCase();
        PricingResponse pricing = getPublicPricing();
        String campaignId = pricing.activeSale() == null ? null : pricing.activeSale().id();
        if (normalized.equals("monthly") || normalized.equals("premium_monthly")) {
            return new ResolvedPlan(pricing.hiPro(), campaignId);
        }
        if (normalized.equals("yearly") || normalized.equals("premium_yearly")) {
            return new ResolvedPlan(pricing.hiMax(), campaignId);
        }
        throw new IllegalArgumentException("Gói thanh toán không hợp lệ");
    }

    public PlanPricingConfig getAdminPricing() {
        return getOrCreateConfig();
    }

    public List<SaleView> listSales() {
        Instant now = Instant.now();
        return saleRepository.findAllByOrderByStartsAtDesc().stream()
                .map(campaign -> toSaleView(campaign, effectiveStatus(campaign, now)))
                .toList();
    }

    @CacheEvict(value = "plan_pricing", allEntries = true)
    public synchronized PlanPricingConfig updatePricing(String actorUserId,
                                                        UpdatePlanPricingRequest request,
                                                        String ipAddress) {
        validateBasePricesAgainstSales(request.getHiProBasePrice(), request.getHiMaxBasePrice());
        PlanPricingConfig config = getOrCreateConfig();
        String before = config.getHiProBasePrice() + "/" + config.getHiMaxBasePrice();
        config.setHiProBasePrice(request.getHiProBasePrice());
        config.setHiMaxBasePrice(request.getHiMaxBasePrice());
        config.setUpdatedBy(actorUserId);
        PlanPricingConfig saved = pricingRepository.save(config);
        audit(actorUserId, "UPDATE_PLAN_PRICING", "PLAN_PRICING", saved.getId(), before,
                saved.getHiProBasePrice() + "/" + saved.getHiMaxBasePrice(), ipAddress);
        return saved;
    }

    @CacheEvict(value = "plan_pricing", allEntries = true)
    public synchronized SaleView createSale(String actorUserId,
                                            UpsertSaleCampaignRequest request,
                                            String ipAddress) {
        PlanPricingConfig pricing = getOrCreateConfig();
        validateSale(request, pricing);
        SaleCampaign campaign = new SaleCampaign();
        apply(campaign, request);
        campaign.setStatus(SaleCampaignStatus.DRAFT);
        campaign.setCreatedBy(actorUserId);
        campaign.setUpdatedBy(actorUserId);
        SaleCampaign saved = saleRepository.save(campaign);
        audit(actorUserId, "CREATE_SALE", "SALE_CAMPAIGN", saved.getId(), null, saved.getName(), ipAddress);
        return toSaleView(saved, saved.getStatus());
    }

    @CacheEvict(value = "plan_pricing", allEntries = true)
    public synchronized SaleView updateSale(String actorUserId,
                                            String saleId,
                                            UpsertSaleCampaignRequest request,
                                            String ipAddress) {
        SaleCampaign campaign = requireSale(saleId);
        if (effectiveStatus(campaign, Instant.now()) == SaleCampaignStatus.ENDED) {
            throw new IllegalArgumentException("Không thể chỉnh sửa chiến dịch đã kết thúc");
        }
        validateSale(request, getOrCreateConfig());
        if (RUNNABLE_STATUSES.contains(campaign.getStatus())) {
            ensureNoOverlap(request.getStartsAt(), request.getEndsAt(), campaign.getId());
        }
        String before = campaign.getName() + ":" + campaign.getHiProSalePrice() + "/" + campaign.getHiMaxSalePrice();
        apply(campaign, request);
        campaign.setUpdatedBy(actorUserId);
        SaleCampaign saved = saleRepository.save(campaign);
        audit(actorUserId, "UPDATE_SALE", "SALE_CAMPAIGN", saved.getId(), before,
                saved.getName() + ":" + saved.getHiProSalePrice() + "/" + saved.getHiMaxSalePrice(), ipAddress);
        return toSaleView(saved, effectiveStatus(saved, Instant.now()));
    }

    @CacheEvict(value = "plan_pricing", allEntries = true)
    public synchronized SaleView activateSale(String actorUserId, String saleId, String ipAddress) {
        SaleCampaign campaign = requireSale(saleId);
        Instant now = Instant.now();
        if (!campaign.getEndsAt().isAfter(now)) {
            throw new IllegalArgumentException("Chiến dịch đã hết thời gian chạy");
        }
        ensureNoOverlap(campaign.getStartsAt(), campaign.getEndsAt(), campaign.getId());
        campaign.setStatus(campaign.getStartsAt().isAfter(now)
                ? SaleCampaignStatus.SCHEDULED
                : SaleCampaignStatus.ACTIVE);
        campaign.setUpdatedBy(actorUserId);
        SaleCampaign saved = saleRepository.save(campaign);
        audit(actorUserId, "ACTIVATE_SALE", "SALE_CAMPAIGN", saved.getId(), null, saved.getStatus().name(), ipAddress);
        return toSaleView(saved, effectiveStatus(saved, now));
    }

    @CacheEvict(value = "plan_pricing", allEntries = true)
    public synchronized SaleView disableSale(String actorUserId, String saleId, String ipAddress) {
        SaleCampaign campaign = requireSale(saleId);
        campaign.setStatus(SaleCampaignStatus.DISABLED);
        campaign.setUpdatedBy(actorUserId);
        SaleCampaign saved = saleRepository.save(campaign);
        audit(actorUserId, "DISABLE_SALE", "SALE_CAMPAIGN", saved.getId(), null, "DISABLED", ipAddress);
        return toSaleView(saved, SaleCampaignStatus.DISABLED);
    }

    @Scheduled(fixedRate = 60_000)
    @CacheEvict(value = "plan_pricing", allEntries = true)
    public void refreshPricingCache() {
        // Scheduled eviction lets campaigns start and end without an application restart.
    }

    private PlanPricingConfig getOrCreateConfig() {
        return pricingRepository.findById(PlanPricingConfig.DEFAULT_ID).orElseGet(() -> {
            PlanPricingConfig config = new PlanPricingConfig();
            config.setId(PlanPricingConfig.DEFAULT_ID);
            return pricingRepository.save(config);
        });
    }

    private void validateBasePricesAgainstSales(long hiProBasePrice, long hiMaxBasePrice) {
        Instant now = Instant.now();
        saleRepository.findByStatusInOrderByStartsAtDesc(RUNNABLE_STATUSES).stream()
                .filter(campaign -> campaign.getEndsAt().isAfter(now))
                .forEach(campaign -> {
            if (campaign.getHiProSalePrice() >= hiProBasePrice || campaign.getHiMaxSalePrice() >= hiMaxBasePrice) {
                throw new IllegalArgumentException("Giá gốc mới phải cao hơn giá sale đang chạy hoặc đã lên lịch");
            }
        });
    }

    private void validateSale(UpsertSaleCampaignRequest request, PlanPricingConfig pricing) {
        if (!request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (request.getHiProSalePrice() < 0 || request.getHiMaxSalePrice() < 0) {
            throw new IllegalArgumentException("Giá sale phải lớn hơn 0");
        }
        if (request.getHiProSalePrice() >= pricing.getHiProBasePrice()
                || request.getHiMaxSalePrice() >= pricing.getHiMaxBasePrice()) {
            throw new IllegalArgumentException("Giá sale phải thấp hơn giá gốc của từng gói");
        }
    }

    private void ensureNoOverlap(Instant startsAt, Instant endsAt, String excludedId) {
        boolean overlaps = saleRepository.findByStatusInOrderByStartsAtDesc(RUNNABLE_STATUSES).stream()
                .filter(campaign -> !Objects.equals(campaign.getId(), excludedId))
                .filter(campaign -> campaign.getEndsAt().isAfter(Instant.now()))
                .anyMatch(campaign -> startsAt.isBefore(campaign.getEndsAt()) && endsAt.isAfter(campaign.getStartsAt()));
        if (overlaps) throw new IllegalArgumentException("Thời gian chiến dịch bị trùng với một chương trình sale khác");
    }

    private SaleCampaign requireSale(String saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chiến dịch sale"));
    }

    private void apply(SaleCampaign campaign, UpsertSaleCampaignRequest request) {
        campaign.setName(request.getName().trim());
        campaign.setTitle(request.getTitle().trim());
        campaign.setSubtitle(request.getSubtitle() == null ? "" : request.getSubtitle().trim());
        campaign.setHiProSalePrice(request.getHiProSalePrice());
        campaign.setHiMaxSalePrice(request.getHiMaxSalePrice());
        campaign.setStartsAt(request.getStartsAt());
        campaign.setEndsAt(request.getEndsAt());
    }

    private SaleCampaignStatus effectiveStatus(SaleCampaign campaign, Instant now) {
        if (campaign.getStatus() == SaleCampaignStatus.DISABLED || campaign.getStatus() == SaleCampaignStatus.DRAFT) {
            return campaign.getStatus();
        }
        if (!campaign.getEndsAt().isAfter(now)) return SaleCampaignStatus.ENDED;
        if (campaign.getStartsAt().isAfter(now)) return SaleCampaignStatus.SCHEDULED;
        return SaleCampaignStatus.ACTIVE;
    }

    private PlanPrice plan(String id, String code, String name, int durationDays, long basePrice, Long salePrice) {
        long currentPrice = salePrice == null ? basePrice : salePrice;
        int discountPercent = salePrice == null ? 0 : (int) Math.round((basePrice - salePrice) * 100.0 / basePrice);
        return new PlanPrice(id, code, name, durationDays, basePrice, currentPrice, discountPercent);
    }

    private SaleView toSaleView(SaleCampaign campaign, SaleCampaignStatus status) {
        return new SaleView(campaign.getId(), campaign.getName(), campaign.getTitle(), campaign.getSubtitle(),
                campaign.getHiProSalePrice(), campaign.getHiMaxSalePrice(), campaign.getStartsAt(),
                campaign.getEndsAt(), status, campaign.getCreatedAt(), campaign.getUpdatedAt());
    }

    private void audit(String actorUserId, String action, String entityType, String entityId,
                       String before, String after, String ipAddress) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setBeforeData(before);
        log.setAfterData(after);
        log.setIpAddress(ipAddress);
        auditLogRepository.save(log);
    }

    public record PricingResponse(PlanPrice hiPro, PlanPrice hiMax, SaleView activeSale) {}

    public record PlanPrice(String id, String code, String name, int durationDays,
                            long basePrice, long currentPrice, int discountPercent) {}

    public record ResolvedPlan(PlanPrice plan, String campaignId) {}

    public record SaleView(String id, String name, String title, String subtitle,
                           long hiProSalePrice, long hiMaxSalePrice,
                           Instant startsAt, Instant endsAt, SaleCampaignStatus status,
                           Instant createdAt, Instant updatedAt) {}
}
