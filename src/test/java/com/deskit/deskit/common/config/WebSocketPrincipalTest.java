package com.deskit.deskit.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketPrincipalTest {

    @Test
    void usesAnonymousWhenNameIsNullOrBlank() {
        assertThat(new WebSocketPrincipal(null).getName()).isEqualTo("anonymous");
        assertThat(new WebSocketPrincipal(" ").getName()).isEqualTo("anonymous");
    }

    @Test
    void keepsProvidedName() {
        assertThat(new WebSocketPrincipal("user1").getName()).isEqualTo("user1");
    }
}
