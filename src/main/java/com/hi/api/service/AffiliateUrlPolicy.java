package com.hi.api.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@Component
public class AffiliateUrlPolicy {

    private static final List<String> ALLOWED_HOSTS = List.of(
            "shopee.vn",
            "shopee.com",
            "shope.ee",
            "tiktok.com",
            "tiktokshop.com",
            "tiktokglobalshop.com"
    );

    public String normalizeAndValidate(String rawUrl) {
        String text = rawUrl == null ? "" : rawUrl.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Link affiliate không được để trống");
        }
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://" + text;
        }

        try {
            URI uri = URI.create(text).normalize();
            validateUri(uri);
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Link affiliate")) {
                throw ex;
            }
            throw new IllegalArgumentException("Link affiliate không hợp lệ");
        }
    }

    public String resolveRedirect(String currentUrl, String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Link affiliate chuyển hướng không hợp lệ");
        }
        URI resolved = URI.create(currentUrl).resolve(location).normalize();
        validateUri(resolved);
        return resolved.toString();
    }

    boolean isAllowedHost(String host) {
        if (host == null || host.isBlank()) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return ALLOWED_HOSTS.stream()
                .anyMatch(allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed));
    }

    private void validateUri(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Link affiliate chỉ hỗ trợ http hoặc https");
        }
        if (uri.getUserInfo() != null || uri.getHost() == null || !isAllowedHost(uri.getHost())) {
            throw new IllegalArgumentException("Link affiliate chỉ hỗ trợ tên miền TikTok Shop hoặc Shopee");
        }
        if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
            throw new IllegalArgumentException("Link affiliate sử dụng cổng không được hỗ trợ");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Link affiliate trỏ tới địa chỉ mạng không an toàn");
                }
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Không thể xác minh tên miền của link affiliate");
        }
    }
}
