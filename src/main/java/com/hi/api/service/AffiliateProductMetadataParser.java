package com.hi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Iterator;

@Component
public class AffiliateProductMetadataParser {

    private final ObjectMapper objectMapper;

    public AffiliateProductMetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductMetadata parse(Document document, String pageUrl) {
        ProductMetadata jsonLd = parseJsonLd(document, pageUrl);
        String title = firstNonBlank(
                jsonLd.title(),
                meta(document, "property", "og:title"),
                meta(document, "name", "twitter:title"),
                meta(document, "itemprop", "name"),
                document.title()
        );
        String description = firstNonBlank(
                jsonLd.description(),
                meta(document, "property", "og:description"),
                meta(document, "name", "twitter:description"),
                meta(document, "itemprop", "description"),
                meta(document, "name", "description")
        );
        String imageUrl = normalizeResourceUrl(pageUrl, firstNonBlank(
                jsonLd.imageUrl(),
                meta(document, "property", "og:image"),
                meta(document, "name", "twitter:image"),
                meta(document, "itemprop", "image"),
                meta(document, "property", "og:image:secure_url")
        ));
        BigDecimal price = jsonLd.price() != null ? jsonLd.price() : parsePrice(firstNonBlank(
                meta(document, "property", "product:price:amount"),
                meta(document, "property", "og:price:amount"),
                meta(document, "itemprop", "price"),
                meta(document, "name", "twitter:data1")
        ));
        String sourceName = firstNonBlank(
                jsonLd.sourceName(),
                meta(document, "property", "og:site_name")
        );
        return new ProductMetadata(title, description, imageUrl, price, sourceName);
    }

    private ProductMetadata parseJsonLd(Document document, String pageUrl) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data().isBlank() ? script.html() : script.data());
                JsonNode product = findProduct(root);
                if (product == null) continue;

                JsonNode offers = firstObject(product.get("offers"));
                String image = imageValue(product.get("image"));
                String sourceName = text(product.path("brand").path("name"));
                if (sourceName == null) sourceName = text(product.path("seller").path("name"));
                return new ProductMetadata(
                        text(product.get("name")),
                        text(product.get("description")),
                        normalizeResourceUrl(pageUrl, image),
                        parsePrice(offers != null ? text(offers.get("price")) : null),
                        sourceName
                );
            } catch (Exception ignored) {
                // Một trang có thể chứa nhiều JSON-LD; tiếp tục thử block kế tiếp.
            }
        }
        return ProductMetadata.empty();
    }

    private JsonNode findProduct(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findProduct(child);
                if (found != null) return found;
            }
            return null;
        }
        if (!node.isObject()) return null;

        JsonNode type = node.get("@type");
        if (type != null) {
            if (type.isTextual() && "product".equalsIgnoreCase(type.asText())) return node;
            if (type.isArray()) {
                for (JsonNode value : type) {
                    if ("product".equalsIgnoreCase(value.asText())) return node;
                }
            }
        }
        JsonNode graph = node.get("@graph");
        if (graph != null) {
            JsonNode found = findProduct(graph);
            if (found != null) return found;
        }
        Iterator<JsonNode> children = node.elements();
        while (children.hasNext()) {
            JsonNode found = findProduct(children.next());
            if (found != null) return found;
        }
        return null;
    }

    private JsonNode firstObject(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) return node;
        if (node.isArray() && !node.isEmpty()) return node.get(0);
        return null;
    }

    private String imageValue(JsonNode image) {
        if (image == null || image.isNull()) return null;
        if (image.isTextual()) return image.asText();
        if (image.isArray() && !image.isEmpty()) return imageValue(image.get(0));
        if (image.isObject()) return firstNonBlank(text(image.get("url")), text(image.get("contentUrl")));
        return null;
    }

    private String meta(Document doc, String attr, String key) {
        Element element = doc.selectFirst("meta[" + attr + "='" + key + "']");
        return element != null ? element.attr("content").trim() : null;
    }

    private String normalizeResourceUrl(String pageUrl, String resourceUrl) {
        if (resourceUrl == null || resourceUrl.isBlank()) return null;
        try {
            return URI.create(pageUrl).resolve(resourceUrl.trim()).toString();
        } catch (Exception ignored) {
            return resourceUrl.trim();
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isValueNode() && !node.asText().isBlank() ? node.asText().trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String cleaned = raw.replaceAll("[^0-9.,]", "");
            if (cleaned.isBlank()) return null;
            if (cleaned.contains(",") && cleaned.contains(".")) {
                cleaned = cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')
                        ? cleaned.replace(".", "").replace(",", ".")
                        : cleaned.replace(",", "");
            } else if (cleaned.contains(",")) {
                cleaned = cleaned.replace(",", "");
            } else if (cleaned.matches("\\d{1,3}(\\.\\d{3})+")) {
                cleaned = cleaned.replace(".", "");
            }
            return new BigDecimal(cleaned);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record ProductMetadata(
            String title,
            String description,
            String imageUrl,
            BigDecimal price,
            String sourceName
    ) {
        static ProductMetadata empty() {
            return new ProductMetadata(null, null, null, null, null);
        }
    }
}
