package com.hi.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeaderCsrfTokenRepositoryTest {

    @Test
    void returnsOnlyTokensIssuedByTheServer() {
        HeaderCsrfTokenRepository repository = new HeaderCsrfTokenRepository();
        MockHttpServletRequest issueRequest = new MockHttpServletRequest("GET", "/api/auth/csrf");
        CsrfToken issued = repository.generateToken(issueRequest);
        repository.saveToken(issued, issueRequest, new MockHttpServletResponse());

        MockHttpServletRequest validRequest = new MockHttpServletRequest("POST", "/api/auth/google");
        validRequest.addHeader(HeaderCsrfTokenRepository.HEADER_NAME, issued.getToken());

        CsrfToken loaded = repository.loadToken(validRequest);
        assertEquals(issued.getToken(), loaded.getToken());
        assertEquals(HeaderCsrfTokenRepository.HEADER_NAME, loaded.getHeaderName());

        MockHttpServletRequest unknownRequest = new MockHttpServletRequest("POST", "/api/auth/google");
        unknownRequest.addHeader(HeaderCsrfTokenRepository.HEADER_NAME, "unknown-token");
        assertNull(repository.loadToken(unknownRequest));
    }
}
