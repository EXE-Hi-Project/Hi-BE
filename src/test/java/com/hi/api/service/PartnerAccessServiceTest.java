package com.hi.api.service;

import com.hi.api.model.User;
import com.hi.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private User user(String id, String partnerId) {
        User user = new User();
        user.setId(id);
        user.setPartnerId(partnerId);
        return user;
    }
}
