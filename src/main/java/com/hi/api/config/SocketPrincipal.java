package com.hi.api.config;

import java.security.Principal;

public record SocketPrincipal(String name) implements Principal {
    @Override
    public String getName() {
        return name;
    }
}
