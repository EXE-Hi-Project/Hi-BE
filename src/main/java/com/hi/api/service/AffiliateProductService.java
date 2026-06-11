package com.hi.api.service;

import com.hi.api.dto.request.UpsertAffiliateProductRequest;
import com.hi.api.dto.request.UpsertAffiliateRevenueRequest;
import com.hi.api.model.AffiliateCommissionSource;
import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.model.AffiliateRevenueEvent;
import com.hi.api.repository.AffiliateProductRepository;
import com.hi.api.repository.AffiliateRevenueEventRepository;
import com.hi.api.repository.ClickTrackingRepository;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
public class AffiliateProductService {

    private final AffiliateProductRepository affiliateProductRepository;
    private final AffiliateRevenueEventRepository affiliateRevenueEventRepository;
    private final ClickTrackingRepository clickTrackingRepository;
    private final SequenceService sequenceService;
    private final AffiliateTiktokClient affiliateTiktokClient;
    private final AffiliateShopeeClient affiliateShopeeClient;
    private final MongoTemplate mongoTemplate;
    private final AffiliateUrlPolicy affiliateUrlPolicy;
    private final AffiliateProductMetadataParser metadataParser;

    public AffiliateProductService(AffiliateProductRepository affiliateProductRepository,
                                   AffiliateRevenueEventRepository affiliateRevenueEventRepository,
                                   ClickTrackingRepository clickTrackingRepository,
                                   SequenceService sequenceService,
                                   AffiliateTiktokClient affiliateTiktokClient,
                                   AffiliateShopeeClient affiliateShopeeClient,
                                   MongoTemplate mongoTemplate,
                                   AffiliateUrlPolicy affiliateUrlPolicy,
                                   AffiliateProductMetadataParser metadataParser) {
        this.affiliateProductRepository = affiliateProductRepository;
        this.affiliateRevenueEventRepository = affiliateRevenueEventRepository;
        this.clickTrackingRepository = clickTrackingRepository;
        this.sequenceService = sequenceService;
        this.affiliateTiktokClient = affiliateTiktokClient;
        this.affiliateShopeeClient = affiliateShopeeClient;
        this.mongoTemplate = mongoTemplate;
        this.affiliateUrlPolicy = affiliateUrlPolicy;
        this.metadataParser = metadataParser;
    }

    public List<AffiliateProduct> searchProducts(String q, AffiliatePlatform platform, String symptomCategory, Boolean active, int limit) {
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String text = q.trim();
            criteria.add(new Criteria().orOperator(
                    Criteria.where("name").regex(text, "i"),
                    Criteria.where("description").regex(text, "i"),
                    Criteria.where("symptomTags").regex(text, "i"),
                    Criteria.where("category").regex(text, "i")
            ));
        }
        if (platform != null) criteria.add(Criteria.where("platform").is(platform));
        if (symptomCategory != null && !symptomCategory.isBlank()) {
            String text = symptomCategory.trim();
            criteria.add(new Criteria().orOperator(
                    Criteria.where("symptomCategory").regex("^" + java.util.regex.Pattern.quote(text) + "$", "i"),
                    Criteria.where("symptomTags").regex(text, "i")
            ));
        }
        if (active != null) criteria.add(Criteria.where("isActive").is(active));

        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }

        query.with(Sort.by(
                Sort.Order.desc("priority"),
                Sort.Order.desc("commissionRate"),
                Sort.Order.asc("price"),
                Sort.Order.asc("name")
        ));
        query.limit(Math.min(Math.max(limit, 1), 100));
        return mongoTemplate.find(query, AffiliateProduct.class);
    }

    public List<AffiliateProduct> getRecommendations(String symptomCategory, String phase, int limit) {
        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("isActive").is(true));
        if (symptomCategory != null && !symptomCategory.isBlank()) {
            String text = symptomCategory.trim();
            filters.add(new Criteria().orOperator(
                    Criteria.where("symptomCategory").regex(text, "i"),
                    Criteria.where("symptomTags").regex(text, "i"),
                    Criteria.where("name").regex(text, "i"),
                    Criteria.where("symptomTags").size(0),
                    Criteria.where("symptomTags").exists(false),
                    Criteria.where("symptomCategory").in(null, "")
            ));
        }
        if (phase != null && !phase.isBlank()) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("phaseTags").regex(phase.trim(), "i"),
                    Criteria.where("phaseTags").size(0),
                    Criteria.where("phaseTags").exists(false)
            ));
        }
        query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("commissionRate"), Sort.Order.asc("price")));
        query.limit(Math.min(Math.max(limit, 1), 12));
        return mongoTemplate.find(query, AffiliateProduct.class);
    }

    public Map<String, Object> previewLink(String rawUrl) {
        String normalizedUrl = affiliateUrlPolicy.normalizeAndValidate(rawUrl);
        AffiliatePlatform platform = detectPlatform(normalizedUrl);
        if (AffiliatePlatform.OTHER.equals(platform)) {
            throw new IllegalArgumentException("Hi hiện chỉ hỗ trợ đọc trước link TikTok Shop hoặc Shopee trong bản MVP này.");
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("normalizedUrl", normalizedUrl);
        preview.put("platform", platform);
        preview.put("sourceName", hostName(normalizedUrl));

        try {
            FetchResult fetchResult = fetchDocument(normalizedUrl);
            normalizedUrl = fetchResult.finalUrl();
            platform = detectPlatform(normalizedUrl);
            preview.put("normalizedUrl", normalizedUrl);
            preview.put("platform", platform);

            AffiliateProductMetadataParser.ProductMetadata metadata = metadataParser.parse(
                    fetchResult.document(),
                    normalizedUrl
            );
            preview.put("title", metadata.title());
            preview.put("description", metadata.description());
            preview.put("imageUrl", metadata.imageUrl());
            preview.put("price", metadata.price());
            preview.put("sourceName", firstNonBlank(metadata.sourceName(), hostName(normalizedUrl)));
            preview.put("confidence", confidence(metadata.title(), metadata.description(), metadata.imageUrl()));
        } catch (Exception ex) {
            preview.put("confidence", "LOW");
            preview.put("errorMessage", "Không đọc được đầy đủ metadata từ link này. Bạn vẫn có thể nhập bổ sung thủ công.");
        }

        preview.put("missingFields", missingFields(preview));
        return preview;
    }

    public AffiliateProduct getById(Long id) {
        return affiliateProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm affiliate"));
    }

    @CacheEvict(value = "ai_context", allEntries = true)
    public AffiliateProduct createProduct(UpsertAffiliateProductRequest req) {
        AffiliateProduct product = new AffiliateProduct();
        product.setId(sequenceService.next("affiliate_products"));
        apply(product, req);
        return affiliateProductRepository.save(product);
    }

    @CacheEvict(value = "ai_context", allEntries = true)
    public AffiliateProduct updateProduct(Long id, UpsertAffiliateProductRequest req) {
        AffiliateProduct product = getById(id);
        apply(product, req);
        return affiliateProductRepository.save(product);
    }

    @CacheEvict(value = "ai_context", allEntries = true)
    public void deleteProduct(Long id) {
        AffiliateProduct product = getById(id);
        product.setIsActive(false);
        product.setStatus("ARCHIVED");
        affiliateProductRepository.save(product);
    }

    @CacheEvict(value = "ai_context", allEntries = true)
    public Map<String, Object> sync(AffiliatePlatform platform) {
        if (platform == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("platform", "ALL");
            result.put("results", List.of(sync(AffiliatePlatform.TIKTOK), sync(AffiliatePlatform.SHOPEE)));
            result.put("syncedAt", Instant.now());
            return result;
        }
        return switch (platform) {
            case TIKTOK -> syncFromPlatform(AffiliatePlatform.TIKTOK, affiliateTiktokClient.fetchProducts());
            case SHOPEE -> syncFromPlatform(AffiliatePlatform.SHOPEE, affiliateShopeeClient.fetchProducts());
            default -> throw new IllegalArgumentException("Nền tảng affiliate chưa được hỗ trợ sync tự động");
        };
    }

    public AffiliateRevenueEvent upsertRevenue(UpsertAffiliateRevenueRequest req) {
        AffiliatePlatform platform = req.getPlatform() != null ? req.getPlatform() : AffiliatePlatform.OTHER;
        String platformOrderId = req.getPlatformOrderId();
        if (platformOrderId == null || platformOrderId.isBlank()) {
            platformOrderId = "manual-" + sequenceService.next("affiliate_revenue_events_manual");
        }

        String finalPlatformOrderId = platformOrderId;
        AffiliateRevenueEvent event = affiliateRevenueEventRepository.findByPlatformAndPlatformOrderId(platform, finalPlatformOrderId)
                .orElseGet(() -> {
                    AffiliateRevenueEvent created = new AffiliateRevenueEvent();
                    created.setId(sequenceService.next("affiliate_revenue_events"));
                    created.setPlatform(platform);
                    created.setPlatformOrderId(finalPlatformOrderId);
                    return created;
                });
        event.setPlatform(platform);
        event.setPlatformOrderId(finalPlatformOrderId);
        event.setProductId(req.getProductId());
        event.setUserId(req.getUserId());
        event.setClickTrackingId(req.getClickTrackingId());
        event.setOrderAmount(req.getOrderAmount() != null ? req.getOrderAmount() : BigDecimal.ZERO);
        event.setCommissionAmount(req.getCommissionAmount() != null ? req.getCommissionAmount() : BigDecimal.ZERO);
        event.setCurrency(value(req.getCurrency(), "VND"));
        event.setStatus(value(req.getStatus(), "PENDING").toUpperCase());
        event.setOrderedAt(req.getOrderedAt() != null ? req.getOrderedAt() : Instant.now());
        event.setSettledAt(req.getSettledAt());
        event.setSourcePayload(req.getSourcePayload());
        return affiliateRevenueEventRepository.save(event);
    }

    public Map<String, Object> getAdminOverview() {
        List<AffiliateRevenueEvent> events = affiliateRevenueEventRepository.findAll();
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal settledCommission = BigDecimal.ZERO;
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        long settledOrders = 0;
        for (AffiliateRevenueEvent event : events) {
            BigDecimal commission = event.getCommissionAmount() != null ? event.getCommissionAmount() : BigDecimal.ZERO;
            BigDecimal amount = event.getOrderAmount() != null ? event.getOrderAmount() : BigDecimal.ZERO;
            totalCommission = totalCommission.add(commission);
            totalOrderAmount = totalOrderAmount.add(amount);
            if ("SETTLED".equalsIgnoreCase(event.getStatus()) || "COMPLETED".equalsIgnoreCase(event.getStatus())) {
                settledCommission = settledCommission.add(commission);
                settledOrders++;
            }
        }

        long clicks = clickTrackingRepository.count();
        long activeProducts = affiliateProductRepository.findByIsActiveTrueOrderByCommissionRateDescPriceAsc().size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts", affiliateProductRepository.count());
        summary.put("activeProducts", activeProducts);
        summary.put("clicks", clicks);
        summary.put("orders", events.size());
        summary.put("settledOrders", settledOrders);
        summary.put("totalOrderAmount", totalOrderAmount);
        summary.put("totalCommission", totalCommission);
        summary.put("settledCommission", settledCommission);
        summary.put("conversionRate", clicks == 0 ? 0.0 : Math.round((events.size() * 10000.0 / clicks)) / 100.0);
        return Map.of(
                "summary", summary,
                "recentRevenueEvents", affiliateRevenueEventRepository.findTop50ByOrderByOrderedAtDesc(),
                "recentClicks", clickTrackingRepository.findTop20ByOrderByClickedAtDesc()
        );
    }

    private Map<String, Object> syncFromPlatform(AffiliatePlatform platform, List<Map<String, Object>> remoteProducts) {
        int created = 0;
        int updated = 0;
        for (Map<String, Object> raw : remoteProducts) {
            if (raw == null) continue;
            String externalProductId = firstString(raw, "external_product_id", "externalProductId", "product_id", "productId", "item_id", "itemId", "id");
            boolean existed = externalProductId != null
                    && affiliateProductRepository.findByPlatformAndExternalProductId(platform, externalProductId).isPresent();
            AffiliateProduct upserted = upsertFromRaw(platform, raw);
            if (upserted != null) {
                if (existed) updated++; else created++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", platform);
        result.put("remoteCount", remoteProducts.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("syncedAt", Instant.now());
        return result;
    }

    @CacheEvict(value = "ai_context", allEntries = true)
    public AffiliateProduct upsertFromRaw(AffiliatePlatform platform, Map<String, Object> raw) {
        String externalProductId = firstString(raw, "external_product_id", "externalProductId", "product_id", "productId", "item_id", "itemId", "id");
        if (externalProductId == null || externalProductId.isBlank()) return null;

        AffiliateProduct product = affiliateProductRepository.findByPlatformAndExternalProductId(platform, externalProductId)
                .orElseGet(() -> {
                    AffiliateProduct created = new AffiliateProduct();
                    created.setId(sequenceService.next("affiliate_products"));
                    created.setPlatform(platform);
                    created.setExternalProductId(externalProductId);
                    return created;
                });

        product.setPlatform(platform);
        product.setExternalProductId(externalProductId);
        product.setName(firstString(raw, "name", "title", "product_name", "productName", "item_name"));
        product.setDescription(value(firstString(raw, "description", "desc", "product_description", "productDescription"), ""));
        product.setPrice(firstBigDecimal(raw, "price", "sale_price", "salePrice", "current_price", "currentPrice"));
        product.setCommissionRate(firstDouble(raw, "commission_rate", "commissionRate", "affiliate_commission_rate", "commission"));
        product.setCommissionAmount(firstBigDecimal(raw, "commission_amount", "commissionAmount"));
        calculateCommission(product, AffiliateCommissionSource.PLATFORM_SYNC);
        product.setAffiliateUrl(value(firstString(raw, "affiliate_url", "affiliateUrl", "deep_link", "deeplink", "url"), ""));
        product.setImageUrl(value(firstString(raw, "image_url", "imageUrl", "thumbnail", "thumbnail_url", "thumbnailUrl"), ""));
        product.setSymptomCategory(value(firstString(raw, "symptom_category", "symptomCategory", "category", "target_category"), ""));
        product.setCategory(value(firstString(raw, "category", "category_name", "categoryName"), product.getSymptomCategory()));
        product.setSourceName(value(firstString(raw, "source_name", "sourceName", "shop_name", "shopName", "seller"), platform.name()));
        product.setCurrency(value(firstString(raw, "currency"), "VND"));
        product.setAudience(value(firstString(raw, "audience", "targetAudience"), "BOTH").toUpperCase());
        product.setStatus(value(firstString(raw, "status"), "ACTIVE").toUpperCase());
        Boolean isActive = firstBoolean(raw, "is_active", "isActive", "active", "available");
        product.setIsActive(isActive != null ? isActive : !"ARCHIVED".equalsIgnoreCase(product.getStatus()));
        product.setLastSyncedAt(Instant.now());

        return affiliateProductRepository.save(product);
    }

    private void apply(AffiliateProduct product, UpsertAffiliateProductRequest req) {
        product.setPlatform(req.getPlatform() != null ? req.getPlatform() : (product.getPlatform() != null ? product.getPlatform() : AffiliatePlatform.OTHER));
        product.setExternalProductId(value(req.getExternalProductId(), product.getExternalProductId() != null ? product.getExternalProductId() : "manual-" + product.getId()));
        product.setName(value(req.getName(), product.getName()));
        product.setDescription(value(req.getDescription(), ""));
        product.setPrice(req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO);
        product.setCommissionRate(req.getCommissionRate() != null ? req.getCommissionRate() : 0.0);
        product.setCommissionAmount(req.getCommissionAmount() != null ? req.getCommissionAmount() : BigDecimal.ZERO);
        calculateCommission(product, req.getCommissionSource() != null
                ? req.getCommissionSource()
                : AffiliateCommissionSource.MANUAL);
        product.setAffiliateUrl(value(req.getAffiliateUrl(), ""));
        product.setImageUrl(value(req.getImageUrl(), ""));
        product.setSymptomCategory(value(req.getSymptomCategory(), ""));
        product.setCategory(value(req.getCategory(), product.getSymptomCategory()));
        product.setSymptomTags(req.getSymptomTags() != null ? req.getSymptomTags() : List.of());
        product.setPhaseTags(req.getPhaseTags() != null ? req.getPhaseTags() : List.of());
        product.setGoalTags(req.getGoalTags() != null ? req.getGoalTags() : List.of());
        product.setAudience(value(req.getAudience(), "BOTH").toUpperCase());
        product.setStatus(value(req.getStatus(), "ACTIVE").toUpperCase());
        product.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        product.setSourceName(value(req.getSourceName(), product.getPlatform().name()));
        product.setCurrency(value(req.getCurrency(), "VND"));
        product.setIsActive(req.getIsActive() != null ? req.getIsActive() : !"ARCHIVED".equalsIgnoreCase(product.getStatus()));
    }

    private String firstString(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    private BigDecimal firstBigDecimal(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) continue;
            try {
                if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) return new BigDecimal(text.replace(",", ""));
            } catch (Exception ignored) {
            }
        }
        return BigDecimal.ZERO;
    }

    private Double firstDouble(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) continue;
            try {
                if (value instanceof Number number) return number.doubleValue();
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) return Double.parseDouble(text.replace("%", ""));
            } catch (Exception ignored) {
            }
        }
        return 0.0;
    }

    private Boolean firstBoolean(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) continue;
            if (value instanceof Boolean bool) return bool;
            String text = String.valueOf(value).trim();
            if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) return true;
            if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) return false;
        }
        return null;
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private AffiliatePlatform detectPlatform(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("shopee.") || lower.contains("s.shopee.")) return AffiliatePlatform.SHOPEE;
        if (lower.contains("tiktok.") || lower.contains("vt.tiktok.")) return AffiliatePlatform.TIKTOK;
        return AffiliatePlatform.OTHER;
    }

    private String hostName(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "";
            return host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String confidence(String title, String description, String imageUrl) {
        int score = 0;
        if (title != null && !title.isBlank()) score++;
        if (description != null && !description.isBlank()) score++;
        if (imageUrl != null && !imageUrl.isBlank()) score++;
        if (score >= 3) return "HIGH";
        if (score == 2) return "MEDIUM";
        return "LOW";
    }

    private List<String> missingFields(Map<String, Object> preview) {
        List<String> missing = new ArrayList<>();
        if (preview.get("title") == null) missing.add("name");
        if (preview.get("description") == null) missing.add("description");
        if (preview.get("imageUrl") == null) missing.add("imageUrl");
        if (preview.get("price") == null) missing.add("price");
        return missing;
    }

    private FetchResult fetchDocument(String initialUrl) throws Exception {
        String currentUrl = initialUrl;
        for (int redirect = 0; redirect <= 5; redirect++) {
            Connection.Response response = Jsoup.connect(currentUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                    .referrer("https://www.google.com/")
                    .timeout(12000)
                    .maxBodySize(2_000_000)
                    .ignoreHttpErrors(true)
                    .followRedirects(false)
                    .execute();

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                currentUrl = affiliateUrlPolicy.resolveRedirect(currentUrl, response.header("Location"));
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException("Nền tảng trả về HTTP " + status);
            }
            return new FetchResult(currentUrl, response.parse());
        }
        throw new IllegalArgumentException("Link affiliate chuyển hướng quá nhiều lần");
    }

    private void calculateCommission(AffiliateProduct product, AffiliateCommissionSource source) {
        BigDecimal price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal amount = product.getCommissionAmount() != null ? product.getCommissionAmount() : BigDecimal.ZERO;
        double rate = product.getCommissionRate() != null ? product.getCommissionRate() : 0.0;

        if (price.signum() > 0 && rate > 0 && amount.signum() <= 0) {
            amount = price.multiply(BigDecimal.valueOf(rate))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            product.setCommissionAmount(amount);
        } else if (price.signum() > 0 && amount.signum() > 0 && rate <= 0) {
            rate = amount.multiply(BigDecimal.valueOf(100))
                    .divide(price, 2, RoundingMode.HALF_UP)
                    .doubleValue();
            product.setCommissionRate(rate);
        }
        product.setCommissionSource(source);
    }

    private record FetchResult(String finalUrl, Document document) {
    }
}
