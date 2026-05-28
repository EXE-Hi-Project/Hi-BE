package com.hi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AffiliateTiktokClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.affiliate.tiktok.products-url:}")
    private String productsUrl;

    @Value("${app.affiliate.tiktok.access-token:}")
    private String accessToken;

    @Value("${app.affiliate.tiktok.auth-header-name:Authorization}")
    private String authHeaderName;

    @Value("${app.affiliate.tiktok.auth-header-prefix:Bearer }")
    private String authHeaderPrefix;

    public AffiliateTiktokClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> fetchProducts() {
        if (productsUrl == null || productsUrl.isBlank()) {
            throw new IllegalArgumentException("Chưa cấu hình TikTok products URL");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (accessToken != null && !accessToken.isBlank()) {
            headers.set(authHeaderName, authHeaderPrefix + accessToken.trim());
        }

        ResponseEntity<String> response = restTemplate.exchange(
                productsUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode listNode = extractListNode(root);
            List<Map<String, Object>> products = new ArrayList<>();
            if (listNode != null && listNode.isArray()) {
                for (JsonNode node : listNode) {
                    Map<String, Object> map = objectMapper.convertValue(node, Map.class);
                    products.add(map);
                }
            }
            return products;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không thể đọc dữ liệu sản phẩm TikTok", ex);
        }
    }

    private JsonNode extractListNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        for (String key : List.of("data", "items", "products", "list", "result")) {
            JsonNode node = root.get(key);
            if (node == null) {
                continue;
            }
            if (node.isArray()) {
                return node;
            }
            for (String childKey : List.of("items", "products", "list", "data")) {
                JsonNode child = node.get(childKey);
                if (child != null && child.isArray()) {
                    return child;
                }
            }
        }
        return null;
    }
}