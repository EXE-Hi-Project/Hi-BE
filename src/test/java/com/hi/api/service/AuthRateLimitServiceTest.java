package com.hi.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimitServiceTest {

    @Test
    void blocksRepeatedRequestsForSameEmail() {
        AuthRateLimitService service = new AuthRateLimitService();

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> service.check("forgot", "USER@EXAMPLE.COM", "127.0.0.1", 5, 15));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> service.check("forgot", "user@example.com", "127.0.0.1", 5, 15)
        );
    }
}
