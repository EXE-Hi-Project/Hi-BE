package com.hi.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderCsrfTokenRepositoryTest {

    @Test
    void returnsOnlyTokensIssuedByTheServer() {
        HeaderCsrfTokenRepository repository = new HeaderCsrfTokenRepository("test-signing-secret");
        MockHttpServletRequest issueRequest = new MockHttpServletRequest("GET", "/api/auth/csrf");
        CsrfToken issued = repository.generateToken(issueRequest);
        repository.saveToken(issued, issueRequest, new MockHttpServletResponse());

        MockHttpServletRequest validRequest = new MockHttpServletRequest("POST", "/api/auth/google");
        validRequest.addHeader(HeaderCsrfTokenRepository.HEADER_NAME, mask(issued.getToken()));

        CsrfToken loaded = new HeaderCsrfTokenRepository("test-signing-secret").loadToken(validRequest);
        assertEquals(issued.getToken(), loaded.getToken());
        assertEquals(HeaderCsrfTokenRepository.HEADER_NAME, loaded.getHeaderName());

        MockHttpServletRequest unknownRequest = new MockHttpServletRequest("POST", "/api/auth/google");
        unknownRequest.addHeader(HeaderCsrfTokenRepository.HEADER_NAME, "unknown-token");
        assertNull(repository.loadToken(unknownRequest));
    }

    private String mask(String token) {
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        byte[] masked = new byte[tokenBytes.length * 2];
        for (int index = 0; index < tokenBytes.length; index++) {
            byte mask = (byte) (index + 1);
            masked[index] = mask;
            masked[tokenBytes.length + index] = (byte) (tokenBytes[index] ^ mask);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(masked);
    }
}
