package com.deskit.deskit.common.config;

import java.security.Principal;

public class WebSocketPrincipal implements Principal {
    private final String name;

    public WebSocketPrincipal(String name) {
        this.name = name == null || name.isBlank() ? "anonymous" : name;
    }

    @Override
    public String getName() {
        return name;
    }
}
