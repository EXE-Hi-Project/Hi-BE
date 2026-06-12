package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerAccessServiceTest {

    @Test
    void requiresReciprocalPartnerConnection() {
        UserRepository repository = mock(UserRepository.class);
        PartnerAccessService service = new PartnerAccessService(repository);
        User first = user("a", "b");
        User second = user("b", "a");
        when(repository.findById("b")).thenReturn(Optional.of(second));

        assertEquals(second, service.requireCurrentPartner(first));
    }

    @Test
    void rejectsOneSidedConnection() {
        UserRepository repository = mock(UserRepository.class);
        PartnerAccessService service = new PartnerAccessService(repository);
        User first = user("a", "b");
        User second = user("b", null);
        when(repository.findById("b")).thenReturn(Optional.of(second));

        assertThrows(AccessDeniedException.class, () -> service.requireCurrentPartner(first));
    }

    @Test
    void partnerHealthSharingDefaultsToDeny() {
        PartnerAccessService service = new PartnerAccessService(mock(UserRepository.class));
        User owner = new User();

        assertFalse(service.canShareCycleData(owner));
        assertFalse(service.canShareMood(owner));
        assertFalse(service.canShareDetailedSymptoms(owner));
        assertFalse(service.canShareHealthNotes(owner));
    }

    @Test
    void partnerHealthSharingRequiresExplicitFlags() {
        PartnerAccessService service = new PartnerAccessService(mock(UserRepository.class));
        User owner = new User();
        User.PartnerSharingPreferences sharing = new User.PartnerSharingPreferences();
        sharing.setShareCycleData(true);
        sharing.setShareMood(true);
        owner.setPartnerSharingPreferences(sharing);

        assertTrue(service.canShareCycleData(owner));
        assertTrue(service.canShareMood(owner));
        assertFalse(service.canShareDetailedSymptoms(owner));
    }

    private User user(String id, String partnerId) {
        User user = new User();
        user.setId(id);
        user.setPartnerId(partnerId);
        return user;
    }
}
