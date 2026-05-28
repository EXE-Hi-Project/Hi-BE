package com.hi.api.service;

import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.repository.AffiliateProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AffiliateProductService {

    private final AffiliateProductRepository affiliateProductRepository;
    private final SequenceService sequenceService;
    private final AffiliateTiktokClient affiliateTiktokClient;
    private final MongoTemplate mongoTemplate;

    public AffiliateProductService(AffiliateProductRepository affiliateProductRepository,
                                   SequenceService sequenceService,
                                   AffiliateTiktokClient affiliateTiktokClient,
                                   MongoTemplate mongoTemplate) {
        this.affiliateProductRepository = affiliateProductRepository;
        this.sequenceService = sequenceService;
        this.affiliateTiktokClient = affiliateTiktokClient;
        this.mongoTemplate = mongoTemplate;
    }

    public List<AffiliateProduct> searchProducts(String q, AffiliatePlatform platform, String symptomCategory, Boolean active, int limit) {
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            criteria.add(new Criteria().orOperator(
                    Criteria.where("name").regex(q.trim(), "i"),
                    Criteria.where("description").regex(q.trim(), "i")
            ));
        }
        if (platform != null) {
            criteria.add(Criteria.where("platform").is(platform));
        }
        if (symptomCategory != null && !symptomCategory.isBlank()) {
            criteria.add(Criteria.where("symptomCategory").regex("^" + java.util.regex.Pattern.quote(symptomCategory.trim()) + "$", "i"));
        }
        if (active != null) {
            criteria.add(Criteria.where("isActive").is(active));
        }

        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }

        query.with(Sort.by(Sort.Order.desc("commissionRate"), Sort.Order.asc("price"), Sort.Order.asc("name")));
        query.limit(Math.min(Math.max(limit, 1), 100));
        return mongoTemplate.find(query, AffiliateProduct.class);
    }

    public List<AffiliateProduct> getRecommendations(String symptomCategory, int limit) {
        return searchProducts(null, null, symptomCategory, true, limit);
    }

    public AffiliateProduct getById(Long id) {
        return affiliateProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm affiliate"));
    }

    public Map<String, Object> syncFromTiktok() {
        List<Map<String, Object>> remoteProducts = affiliateTiktokClient.fetchProducts();
        int created = 0;
        int updated = 0;

        for (Map<String, Object> raw : remoteProducts) {
            if (raw == null) {
                continue;
            }
            String externalProductId = firstString(raw, "external_product_id", "externalProductId", "product_id", "productId", "id");
            boolean existed = externalProductId != null
                    && affiliateProductRepository.findByPlatformAndExternalProductId(AffiliatePlatform.TIKTOK, externalProductId).isPresent();
            AffiliateProduct upserted = upsertFromRaw(AffiliatePlatform.TIKTOK, raw);
            if (upserted != null) {
                if (existed) {
                    updated++;
                } else {
                    created++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", AffiliatePlatform.TIKTOK);
        result.put("remoteCount", remoteProducts.size());
        result.put("created", created);
        result.put("updated", updated);
        result.put("syncedAt", Instant.now());
        return result;
    }

    public AffiliateProduct upsertFromRaw(AffiliatePlatform platform, Map<String, Object> raw) {
        String externalProductId = firstString(raw, "external_product_id", "externalProductId", "product_id", "productId", "id");
        if (externalProductId == null || externalProductId.isBlank()) {
            return null;
        }

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
        product.setName(firstString(raw, "name", "title", "product_name", "productName"));
        product.setDescription(firstString(raw, "description", "desc", "product_description", "productDescription"));
        product.setPrice(firstBigDecimal(raw, "price", "sale_price", "salePrice", "current_price", "currentPrice"));
        product.setCommissionRate(firstDouble(raw, "commission_rate", "commissionRate", "affiliate_commission_rate", "commission"));
        product.setAffiliateUrl(firstString(raw, "affiliate_url", "affiliateUrl", "deep_link", "deeplink", "url"));
        product.setImageUrl(firstString(raw, "image_url", "imageUrl", "thumbnail", "thumbnail_url", "thumbnailUrl"));
        product.setSymptomCategory(firstString(raw, "symptom_category", "symptomCategory", "category", "target_category"));

        Boolean isActive = firstBoolean(raw, "is_active", "isActive", "active", "available");
        product.setIsActive(isActive != null ? isActive : true);
        product.setLastSyncedAt(Instant.now());

        return affiliateProductRepository.save(product);
    }

    private String firstString(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private BigDecimal firstBigDecimal(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                continue;
            }
            try {
                if (value instanceof Number number) {
                    return BigDecimal.valueOf(number.doubleValue());
                }
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return new BigDecimal(text.replace(",", ""));
                }
            } catch (Exception ignored) {
                // ignore malformed price fields from remote payloads
            }
        }
        return BigDecimal.ZERO;
    }

    private Double firstDouble(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                continue;
            }
            try {
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
                String text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return Double.parseDouble(text.replace("%", ""));
                }
            } catch (Exception ignored) {
                // ignore malformed commission fields from remote payloads
            }
        }
        return 0.0;
    }

    private Boolean firstBoolean(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            Object value = raw.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text)) {
                    return false;
                }
            }
        }
        return null;
    }
}