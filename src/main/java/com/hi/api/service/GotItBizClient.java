package com.hi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.model.VoucherOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GotItBizClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gotit.enabled:false}")
    private boolean enabled;

    @Value("${app.gotit.commerce-enabled:false}")
    private boolean commerceEnabled;

    @Value("${app.gotit.mode:mock}")
    private String mode;

    @Value("${app.gotit.base-url:https://api-biz-stg.gotit.vn}")
    private String baseUrl;

    @Value("${app.gotit.api-key:}")
    private String apiKey;

    public GotItBizClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> fetchProducts() {
        if (isMockMode()) {
            return mockProducts();
        }
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Chua cau hinh Got It API key hoac GOTIT_ENABLED=false");
        }

        HttpHeaders headers = gotItHeaders();
        ResponseEntity<String> response = restTemplate.exchange(
                trimBaseUrl() + "/api/v4.0/products",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode productsNode = extractListNode(root);
            if (productsNode == null || !productsNode.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> products = new ArrayList<>();
            for (JsonNode node : productsNode) {
                products.add(normalizeProduct(node));
            }
            return products;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Khong doc duoc danh sach voucher Got It", ex);
        }
    }

    public IssuedVoucher issueVoucher(VoucherOrder order, AffiliateProduct product) {
        if (isMockMode()) {
            String code = "HI-" + order.getOrderCode();
            return new IssuedVoucher(code, "https://gotit.vn/voucher/" + code, "MOCK_ISSUED");
        }
        if (!enabled || !commerceEnabled) {
            throw new IllegalStateException("Chua bat Got It commerce. Dat GOTIT_ENABLED=true va GOTIT_COMMERCE_ENABLED=true sau khi co API key/RSA.");
        }
        throw new IllegalStateException("Got It voucher issuance can cau hinh RSA signing/decryption truoc khi bat production.");
    }

    private HttpHeaders gotItHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-GI-Authorization", apiKey.trim());
        headers.set("Accept-Language", "vi");
        return headers;
    }

    private JsonNode extractListNode(JsonNode root) {
        if (root == null) return null;
        if (root.isArray()) return root;
        for (String key : List.of("data", "products", "items", "list", "result")) {
            JsonNode node = root.get(key);
            if (node == null) continue;
            if (node.isArray()) return node;
            for (String childKey : List.of("products", "items", "list", "data")) {
                JsonNode child = node.get(childKey);
                if (child != null && child.isArray()) return child;
            }
        }
        return null;
    }

    private Map<String, Object> normalizeProduct(JsonNode node) {
        Map<String, Object> product = new LinkedHashMap<>();
        String productId = firstText(node, "productId", "product_id", "id", "code", "productCode");
        String productName = firstText(node, "productNm", "productName", "product_name", "name", "title");
        String brandName = firstText(node, "brandNm", "brandName", "brand_name", "brand", "merchantName");
        String categoryName = firstText(node, "categoryNm", "categoryName", "category_name", "category");
        String imageUrl = firstText(node, "image", "imageUrl", "image_url", "thumbnail", "thumbnailUrl");
        BigDecimal price = firstBigDecimal(node, "price", "value", "denomination", "productPrice", "minPrice");

        product.put("externalProductId", productId);
        product.put("name", productName);
        product.put("description", firstNonBlank(
                firstText(node, "description", "desc", "shortDescription"),
                "Voucher Got It cho an uong, di choi va nhung khoanh khac cham soc nhau."
        ));
        product.put("price", price);
        product.put("affiliateUrl", "https://www.gotit.vn");
        product.put("imageUrl", imageUrl);
        product.put("symptomCategory", firstNonBlank(categoryName, "Got It voucher"));
        product.put("category", firstNonBlank(categoryName, "Voucher doi tac"));
        product.put("sourceName", firstNonBlank(brandName, "Got It"));
        product.put("currency", "VND");
        product.put("audience", "BOTH");
        product.put("status", "ACTIVE");
        product.put("available", true);
        product.put("goalTags", inferGoalTags(productName + " " + brandName + " " + categoryName));
        product.put("sourcePayloadSyncedAt", Instant.now().toString());
        return product;
    }

    private List<String> inferGoalTags(String text) {
        String normalized = normalize(text);
        List<String> tags = new ArrayList<>(List.of("voucher", "gotit", "doi tac"));
        if (containsAny(normalized, "an", "uong", "food", "coffee", "cafe", "tra sua", "restaurant")) {
            tags.addAll(List.of("an uong", "hen ho an uong"));
        }
        if (containsAny(normalized, "cinema", "phim", "spa", "travel", "giai tri", "di choi")) {
            tags.addAll(List.of("di choi", "giai tri"));
        }
        return tags;
    }

    private List<Map<String, Object>> mockProducts() {
        return List.of(
                mockProduct("gotit-food-200k", "Voucher an uong Got It 200K", "Dung cho cafe, tra sua, nha hang doi tac Got It.", 200000, "An uong", "Got It Food", List.of("an uong", "hen ho an uong", "doi tac")),
                mockProduct("gotit-date-300k", "Voucher di choi Got It 300K", "Goi y nhe cho xem phim, spa, giai tri hoac buoi hen cuoi tuan.", 300000, "Di choi", "Got It Experience", List.of("di choi", "giai tri", "ngay dac biet")),
                mockProduct("gotit-care-500k", "Voucher yeu thuong Got It 500K", "Mon qua linh hoat de gui nguoi ay khi sap toi ky niem hoac ngay can quan tam.", 500000, "Qua tang", "Got It Gift", List.of("qua tang", "nguoi ay", "ky niem"))
        );
    }

    private Map<String, Object> mockProduct(String id, String name, String description, int price, String category, String sourceName, List<String> tags) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("externalProductId", id);
        product.put("name", name);
        product.put("description", description);
        product.put("price", BigDecimal.valueOf(price));
        product.put("affiliateUrl", "https://www.gotit.vn");
        product.put("imageUrl", "");
        product.put("symptomCategory", category);
        product.put("category", category);
        product.put("sourceName", sourceName);
        product.put("currency", "VND");
        product.put("audience", "BOTH");
        product.put("status", "ACTIVE");
        product.put("available", true);
        product.put("goalTags", tags);
        return product;
    }

    private boolean isMockMode() {
        return mode == null || mode.isBlank() || "mock".equalsIgnoreCase(mode.trim());
    }

    private String trimBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api-biz-stg.gotit.vn" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) return text;
            }
        }
        return "";
    }

    private BigDecimal firstBigDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) continue;
            try {
                if (value.isNumber()) return value.decimalValue();
                String text = value.asText("").replace(",", "").trim();
                if (!text.isBlank()) return new BigDecimal(text);
            } catch (Exception ignored) {
            }
        }
        return BigDecimal.ZERO;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    public record IssuedVoucher(String code, String link, String status) {
    }
}
