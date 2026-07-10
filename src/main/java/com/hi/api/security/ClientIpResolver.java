package com.hi.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {

    private final List<CidrRange> trustedProxyRanges;

    public ClientIpResolver(@Value("${app.trusted-proxy-cidrs:127.0.0.1/32,::1/128}") String trustedProxyCidrs) {
        this.trustedProxyRanges = Arrays.stream((trustedProxyCidrs == null ? "" : trustedProxyCidrs).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(CidrRange::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalize(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        return normalize(forwardedFor.split(",")[0]);
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return trustedProxyRanges.stream().anyMatch(range -> range.contains(remoteAddress));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    private record CidrRange(BigInteger network, BigInteger mask, int byteLength) {
        static CidrRange parse(String cidr) {
            try {
                String[] parts = cidr.split("/", 2);
                InetAddress address = InetAddress.getByName(parts[0]);
                int byteLength = address.getAddress().length;
                int bits = byteLength * 8;
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
                if (prefix < 0 || prefix > bits) {
                    throw new IllegalArgumentException("Invalid CIDR prefix");
                }
                BigInteger mask = prefix == 0
                        ? BigInteger.ZERO
                        : BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).shiftRight(bits - prefix).shiftLeft(bits - prefix);
                BigInteger network = toBigInteger(address).and(mask);
                return new CidrRange(network, mask, byteLength);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid TRUSTED_PROXY_CIDRS value: " + cidr, e);
            }
        }

        boolean contains(String value) {
            try {
                InetAddress address = InetAddress.getByName(value);
                if (address.getAddress().length != byteLength) {
                    return false;
                }
                return toBigInteger(address).and(mask).equals(network);
            } catch (Exception e) {
                return false;
            }
        }

        private static BigInteger toBigInteger(InetAddress address) {
            return new BigInteger(1, address.getAddress());
        }
    }
}
