package com.hi.api.service;

import com.hi.api.dto.request.UpdateCoupleStartDateRequest;
import com.hi.api.dto.request.UpsertCoupleAnniversaryEventRequest;
import com.hi.api.model.CoupleAnniversary;
import com.hi.api.model.User;
import com.hi.api.repository.CoupleAnniversaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoupleAnniversaryServiceTest {

    private CoupleAnniversaryRepository repository;
    private PartnerAccessService partnerAccessService;
    private CoupleAnniversaryService service;
    private User user;
    private User partner;

    @BeforeEach
    void setUp() {
        repository = mock(CoupleAnniversaryRepository.class);
        partnerAccessService = mock(PartnerAccessService.class);
        service = new CoupleAnniversaryService(repository, partnerAccessService, mock(RealtimeEventService.class));
        user = user("user-a");
        partner = user("user-b");
        user.setPartnerId(partner.getId());
        partner.setPartnerId(user.getId());

        when(partnerAccessService.requireUser(user.getId())).thenReturn(user);
        when(partnerAccessService.requireCurrentPartner(user)).thenReturn(partner);
        when(partnerAccessService.pairKey(user.getId(), partner.getId())).thenReturn("user-a:user-b");
        when(partnerAccessService.isActivePair(user.getId(), partner.getId())).thenReturn(true);
    }

    @Test
    void rejectsFutureStartDate() {
        UpdateCoupleStartDateRequest request = new UpdateCoupleStartDateRequest();
        request.setStartDate(LocalDate.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> service.updateStartDate(user.getId(), request));
        verify(repository, never()).save(any());
    }

    @Test
    void savesStartDatePropertiesCorrectly() {
        UpdateCoupleStartDateRequest request = new UpdateCoupleStartDateRequest();
        request.setStartDate(LocalDate.now().minusDays(1));
        request.setColor("emerald");
        request.setEffect("glow");
        request.setIcon("celebration");
        request.setSticker("ring");

        when(repository.findByPairKeyAndType("user-a:user-b", CoupleAnniversary.Type.START_DATE))
                .thenReturn(Optional.empty());
        when(repository.save(any(CoupleAnniversary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateStartDate(user.getId(), request);

        org.mockito.ArgumentCaptor<CoupleAnniversary> captor = org.mockito.ArgumentCaptor.forClass(CoupleAnniversary.class);
        verify(repository).save(captor.capture());
        CoupleAnniversary saved = captor.getValue();
        assertEquals("emerald", saved.getColor());
        assertEquals("glow", saved.getEffect());
        assertEquals("celebration", saved.getIcon());
        assertEquals("ring", saved.getSticker());
    }

    @Test
    void calculatesDaysTogetherFromStartDate() {
        CoupleAnniversary startDate = new CoupleAnniversary();
        startDate.setEventDate(LocalDate.now().minusDays(4));
        when(repository.findByPairKeyAndType("user-a:user-b", CoupleAnniversary.Type.START_DATE))
                .thenReturn(Optional.of(startDate));
        when(repository.findByPairKeyAndTypeOrderByEventDateAsc("user-a:user-b", CoupleAnniversary.Type.MEMORY))
                .thenReturn(List.of());

        assertEquals(5L, service.getAnniversaries(user.getId()).get("daysTogether"));
    }

    @Test
    void rejectsEventUpdateOutsideCurrentPair() {
        UpsertCoupleAnniversaryEventRequest request = eventRequest();
        when(repository.findByIdAndPairKeyAndType("event-1", "user-a:user-b", CoupleAnniversary.Type.MEMORY))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updateEvent(user.getId(), "event-1", request));
    }

    private UpsertCoupleAnniversaryEventRequest eventRequest() {
        UpsertCoupleAnniversaryEventRequest request = new UpsertCoupleAnniversaryEventRequest();
        request.setEventDate(LocalDate.now());
        request.setTitle("Buổi hẹn đầu tiên");
        request.setColor("pink");
        request.setEffect("none");
        request.setIcon("favorite");
        request.setSticker("heart");
        return request;
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setName(id);
        return user;
    }
}
