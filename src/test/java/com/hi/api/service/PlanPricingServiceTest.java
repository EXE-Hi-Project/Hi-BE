package com.hi.api.service;

import com.hi.api.dto.request.UpsertSaleCampaignRequest;
import com.hi.api.model.PlanPricingConfig;
import com.hi.api.model.SaleCampaign;
import com.hi.api.model.SaleCampaignStatus;
import com.hi.api.repository.AdminAuditLogRepository;
import com.hi.api.repository.PlanPricingConfigRepository;
import com.hi.api.repository.SaleCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanPricingServiceTest {
    private PlanPricingConfigRepository pricingRepository;
    private SaleCampaignRepository saleRepository;
    private PlanPricingService service;

    @BeforeEach
    void setUp() {
        pricingRepository = mock(PlanPricingConfigRepository.class);
        saleRepository = mock(SaleCampaignRepository.class);
        service = new PlanPricingService(pricingRepository, saleRepository, mock(AdminAuditLogRepository.class));

        PlanPricingConfig config = new PlanPricingConfig();
        config.setId(PlanPricingConfig.DEFAULT_ID);
        when(pricingRepository.findById(PlanPricingConfig.DEFAULT_ID)).thenReturn(Optional.of(config));
    }

    @Test
    void publicPricingUsesActiveSale() {
        Instant now = Instant.now();
        SaleCampaign campaign = campaign("sale-1", now.minusSeconds(60), now.plusSeconds(3600));
        when(saleRepository.findByStatusInOrderByStartsAtDesc(any())).thenReturn(List.of(campaign));

        PlanPricingService.PricingResponse pricing = service.getPublicPricing();

        assertEquals("Hi Pro", pricing.hiPro().name());
        assertEquals(39_000L, pricing.hiPro().currentPrice());
        assertEquals(299_000L, pricing.hiMax().currentPrice());
        assertEquals("sale-1", pricing.activeSale().id());
    }

    @Test
    void activatingOverlappingCampaignIsRejected() {
        Instant now = Instant.now();
        SaleCampaign existing = campaign("sale-existing", now.plusSeconds(300), now.plusSeconds(3600));
        SaleCampaign candidate = campaign("sale-new", now.plusSeconds(600), now.plusSeconds(7200));
        candidate.setStatus(SaleCampaignStatus.DRAFT);
        when(saleRepository.findById("sale-new")).thenReturn(Optional.of(candidate));
        when(saleRepository.findByStatusInOrderByStartsAtDesc(any())).thenReturn(List.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.activateSale("admin", "sale-new", "127.0.0.1"));
    }

    @Test
    void salePriceMustBeGreaterThanZero() {
        UpsertSaleCampaignRequest request = new UpsertSaleCampaignRequest();
        request.setName("Zero sale");
        request.setTitle("Zero sale");
        request.setHiProSalePrice(0L);
        request.setHiMaxSalePrice(299_000L);
        request.setStartsAt(Instant.now().plusSeconds(60));
        request.setEndsAt(Instant.now().plusSeconds(3600));

        assertThrows(IllegalArgumentException.class,
                () -> service.createSale("admin", request, "127.0.0.1"));
    }

    @Test
    void salePriceMustBeBelowBasePrice() {
        UpsertSaleCampaignRequest request = new UpsertSaleCampaignRequest();
        request.setName("Sale lỗi");
        request.setTitle("Sale lỗi");
        request.setHiProSalePrice(49_000L);
        request.setHiMaxSalePrice(299_000L);
        request.setStartsAt(Instant.now().plusSeconds(60));
        request.setEndsAt(Instant.now().plusSeconds(3600));

        assertThrows(IllegalArgumentException.class,
                () -> service.createSale("admin", request, "127.0.0.1"));
    }

    private SaleCampaign campaign(String id, Instant startsAt, Instant endsAt) {
        SaleCampaign campaign = new SaleCampaign();
        campaign.setId(id);
        campaign.setName("Summer sale");
        campaign.setTitle("Ưu đãi mùa hè");
        campaign.setSubtitle("Giá tốt trong thời gian giới hạn");
        campaign.setHiProSalePrice(39_000L);
        campaign.setHiMaxSalePrice(299_000L);
        campaign.setStartsAt(startsAt);
        campaign.setEndsAt(endsAt);
        campaign.setStatus(SaleCampaignStatus.SCHEDULED);
        return campaign;
    }
}
