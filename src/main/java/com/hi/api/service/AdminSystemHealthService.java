package com.hi.api.service;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminSystemHealthService {

    private final MongoTemplate mongoTemplate;

    @Value("${spring.ai.model.chat:openai}")
    private String aiProvider;

    @Value("${spring.ai.openai.api-key:}")
    private String aiApiKey;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String aiModel;

    @Value("${app.affiliate.tiktok.products-url:}")
    private String tiktokProductsUrl;

    @Value("${app.affiliate.tiktok.access-token:}")
    private String tiktokAccessToken;

    @Value("${app.affiliate.shopee.products-url:}")
    private String shopeeProductsUrl;

    @Value("${app.affiliate.shopee.access-token:}")
    private String shopeeAccessToken;

    public AdminSystemHealthService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Map<String, Object> getHealth() {
        long mongoStartedAt = System.nanoTime();
        Map<String, Object> mongoStatus;
        try {
            Document result = mongoTemplate.executeCommand("{ ping: 1 }");
            boolean healthy = result.getDouble("ok") == 1.0;
            mongoStatus = service(
                    "MongoDB",
                    healthy ? "HEALTHY" : "DEGRADED",
                    healthy ? "Kết nối cơ sở dữ liệu hoạt động" : "MongoDB phản hồi bất thường",
                    elapsedMillis(mongoStartedAt)
            );
        } catch (Exception ex) {
            mongoStatus = service("MongoDB", "UNAVAILABLE", "Không thể kết nối cơ sở dữ liệu", elapsedMillis(mongoStartedAt));
        }

        Map<String, Object> aiStatus;
        if (!"none".equalsIgnoreCase(aiProvider) && configured(aiApiKey) && !"disabled".equalsIgnoreCase(aiApiKey)) {
            aiStatus = service("AI provider", "HEALTHY", "Đã cấu hình " + value(aiModel, "AI model") + "; không gọi model khi kiểm tra", null);
        } else {
            aiStatus = service("AI provider", "DEGRADED", "Chưa cấu hình provider; Hi AI đang dùng câu trả lời fallback", null);
        }

        List<Map<String, Object>> services = List.of(
                service("Hi API", "HEALTHY", "Backend đang phản hồi", 0L),
                mongoStatus,
                aiStatus,
                connector("TikTok Shop", tiktokProductsUrl, tiktokAccessToken),
                connector("Shopee", shopeeProductsUrl, shopeeAccessToken)
        );

        String overall = services.stream().anyMatch(item -> "UNAVAILABLE".equals(item.get("status")))
                ? "DEGRADED"
                : services.stream().allMatch(item -> "HEALTHY".equals(item.get("status")))
                    ? "HEALTHY"
                    : "DEGRADED";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallStatus", overall);
        result.put("checkedAt", Instant.now());
        result.put("services", services);
        return result;
    }

    private Map<String, Object> connector(String name, String productsUrl, String accessToken) {
        if (!configured(productsUrl)) {
            return service(name, "NOT_CONFIGURED", "Chưa cấu hình products URL", null);
        }
        if (!configured(accessToken)) {
            return service(name, "DEGRADED", "Đã có products URL nhưng chưa có access token", null);
        }
        return service(name, "HEALTHY", "Đã cấu hình connector; không gọi API nền tảng khi kiểm tra", null);
    }

    private Map<String, Object> service(String name, String status, String message, Long latencyMs) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("status", status);
        item.put("message", message);
        if (latencyMs != null) item.put("latencyMs", latencyMs);
        return item;
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value, String fallback) {
        return configured(value) ? value : fallback;
    }
}
