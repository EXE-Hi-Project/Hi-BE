package com.hi.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hi.api.dto.request.UpsertAffiliateProductRequest;
import com.hi.api.model.AffiliateCommissionSource;
import com.hi.api.model.AffiliatePlatform;
import com.hi.api.model.AffiliateProduct;
import com.hi.api.repository.AffiliateProductRepository;
import com.hi.api.repository.AffiliateRevenueEventRepository;
import com.hi.api.repository.ClickTrackingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
                new AffiliateProductMetadataParser(new ObjectMapper())
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
}
