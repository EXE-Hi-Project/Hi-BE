package com.hi.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffiliateUrlPolicyTest {

    private final AffiliateUrlPolicy policy = new AffiliateUrlPolicy();

    @Test
    void acceptsOnlyOfficialPlatformHosts() {
        assertTrue(policy.isAllowedHost("shopee.vn"));
        assertTrue(policy.isAllowedHost("s.shopee.vn"));
        assertTrue(policy.isAllowedHost("shop.tiktok.com"));
        assertFalse(policy.isAllowedHost("shopee.vn.attacker.example"));
        assertFalse(policy.isAllowedHost("example.com"));
    }

    @Test
    void rejectsUnsupportedOrCredentialedUrlsBeforeFetching() {
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeAndValidate("https://example.com/product"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeAndValidate("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> policy.normalizeAndValidate("https://user:pass@shopee.vn/product"));
    }

    @Test
    void validatesRedirectDestination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.resolveRedirect("https://shopee.vn/product", "https://127.0.0.1/internal")
        );
    }
}
