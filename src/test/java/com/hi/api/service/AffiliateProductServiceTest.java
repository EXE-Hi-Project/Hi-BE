package com.hi.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.dto.request.UpsertAffiliateProductRequest;
import com.hi.api.model.AffiliateCommissionSource;
import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.repository.AffiliateProductRepository;
import com.hi.api.repository.AffiliateRevenueEventRepository;
import com.hi.api.repository.ClickTrackingRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AffiliateProductServiceTest {

    @Test
    void calculatesEstimatedCommissionAmountFromRate() {
        AffiliateProductRepository productRepository = mock(AffiliateProductRepository.class);
        SequenceService sequenceService = mock(SequenceService.class);
        when(sequenceService.next("affiliate_products")).thenReturn(10L);
        when(productRepository.save(any(AffiliateProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AffiliateProductService service = new AffiliateProductService(
                productRepository,
                mock(AffiliateRevenueEventRepository.class),
                mock(ClickTrackingRepository.class),
                sequenceService,
                mock(AffiliateTiktokClient.class),
                mock(AffiliateShopeeClient.class),
                mock(MongoTemplate.class),
                new AffiliateUrlPolicy(),
                new AffiliateProductMetadataParser(new ObjectMapper()),
                mock(RealtimeEventService.class)
        );

        UpsertAffiliateProductRequest request = new UpsertAffiliateProductRequest();
        request.setPlatform(AffiliatePlatform.TIKTOK);
        request.setName("Sản phẩm");
        request.setPrice(new BigDecimal("200000"));
        request.setCommissionRate(12.5);
        request.setCommissionSource(AffiliateCommissionSource.ESTIMATED);

        AffiliateProduct product = service.createProduct(request);

        assertEquals(new BigDecimal("25000"), product.getCommissionAmount());
        assertEquals(AffiliateCommissionSource.ESTIMATED, product.getCommissionSource());
    }

    @Test
    void searchProductsEscapesRegexInput() {
        AffiliateProductRepository productRepository = mock(AffiliateProductRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.find(any(Query.class), eq(AffiliateProduct.class))).thenReturn(List.of());

        AffiliateProductService service = new AffiliateProductService(
                productRepository,
                mock(AffiliateRevenueEventRepository.class),
                mock(ClickTrackingRepository.class),
                mock(SequenceService.class),
                mock(AffiliateTiktokClient.class),
                mock(AffiliateShopeeClient.class),
                mongoTemplate,
                new AffiliateUrlPolicy(),
                new AffiliateProductMetadataParser(new ObjectMapper()),
                mock(RealtimeEventService.class)
        );

        service.searchProducts(".*(a+)+$", null, "[period]", null, 20);

        ArgumentCaptor<Query> queryCaptor = forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(AffiliateProduct.class));
        String queryJson = queryCaptor.getValue().getQueryObject().toJson();

        assertTrue(queryJson.contains("\\\\Q.*(a+)+$\\\\E"));
        assertTrue(queryJson.contains("\\\\Q[period]\\\\E"));
    }
}
