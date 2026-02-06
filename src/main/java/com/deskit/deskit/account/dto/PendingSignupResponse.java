package com.deskit.deskit.account.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PendingSignupResponse {

    // provider + providerId로 된 이름
    private String username;

    // provider상 이름
    private String name;

    // provider 제공 이메일
    private String email;
}
