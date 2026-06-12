package com.hi.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilSecurityTest {

    @Test
    void rejectsMissingWeakAndDocumentedDefaultSecrets() {
        JwtUtil jwtUtil = new JwtUtil();

        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "");
        assertThrows(IllegalStateException.class, jwtUtil::validateJwtSecret);

        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "short-secret");
        assertThrows(IllegalStateException.class, jwtUtil::validateJwtSecret);

        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", "HiLover_Secret_2026_ChangeMe");
        assertThrows(IllegalStateException.class, jwtUtil::validateJwtSecret);
    }

    @Test
    void acceptsStrongSecret() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(
                jwtUtil,
                "jwtSecret",
                "0123456789abcdef0123456789abcdef"
        );

        assertDoesNotThrow(jwtUtil::validateJwtSecret);
    }
}
