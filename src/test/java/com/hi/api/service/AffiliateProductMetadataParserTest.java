package com.hi.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AffiliateProductMetadataParserTest {

    private final AffiliateProductMetadataParser parser = new AffiliateProductMetadataParser(new ObjectMapper());

    @Test
    void prefersJsonLdProductAndResolvesRelativeImage() {
        Document document = Jsoup.parse("""
                <html><head>
                  <meta property="og:title" content="Fallback title">
                  <script type="application/ld+json">
                    {
                      "@context": "https://schema.org",
                      "@type": "Product",
                      "name": "Túi chườm ấm",
                      "description": "Giữ ấm vùng bụng",
                      "image": "/images/product.jpg",
                      "brand": { "name": "Hi Shop" },
                      "offers": { "@type": "Offer", "price": "129000" }
                    }
                  </script>
                </head></html>
                """, "https://shopee.vn/products/1");

        AffiliateProductMetadataParser.ProductMetadata metadata = parser.parse(document, "https://shopee.vn/products/1");

        assertEquals("Túi chườm ấm", metadata.title());
        assertEquals("https://shopee.vn/images/product.jpg", metadata.imageUrl());
        assertEquals(new BigDecimal("129000"), metadata.price());
        assertEquals("Hi Shop", metadata.sourceName());
    }

    @Test
    void parsesVietnameseFormattedPrice() {
        assertEquals(new BigDecimal("129000"), parser.parsePrice("129.000 ₫"));
        assertEquals(new BigDecimal("129000"), parser.parsePrice("129,000 VND"));
    }
}
