package com.deskit.deskit.account.oauth;

import com.deskit.deskit.account.dto.UserDTO;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User {

    private final UserDTO userDTO;

    public CustomOAuth2User(UserDTO userDTO) {

        this.userDTO = userDTO;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", userDTO.getUsername());
        attributes.put("role", userDTO.getRole());
        attributes.put("name", userDTO.getName());
        attributes.put("email", userDTO.getEmail());
        attributes.put("profileUrl", userDTO.getProfileUrl());
        attributes.put("newUser", userDTO.isNewUser());
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        Collection<GrantedAuthority> collection = new ArrayList<>();

        collection.add(new GrantedAuthority() {

            @Override
            public String getAuthority() {

                return userDTO.getRole();
            }
        });

        return collection;
    }

    @Override
    public String getName() {
        if (userDTO.getName() != null && !userDTO.getName().isBlank()) {
            return userDTO.getName();
        }
        if (userDTO.getUsername() != null && !userDTO.getUsername().isBlank()) {
            return userDTO.getUsername();
        }
        if (userDTO.getEmail() != null && !userDTO.getEmail().isBlank()) {
            return userDTO.getEmail();
        }
        return "anonymous";
    }

    public String getUsername() {

        return userDTO.getUsername();
    }

    // Expose email for signup screen population.
    public String getEmail() {

        return userDTO.getEmail();
    }

    // Expose whether the user still needs signup completion.
    public boolean isNewUser() {

        return userDTO.isNewUser();
    }

    public String getProfileUrl() {
        return userDTO.getProfileUrl();
    }
}
