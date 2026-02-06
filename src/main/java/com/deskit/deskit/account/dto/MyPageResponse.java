package com.deskit.deskit.account.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@RequiredArgsConstructor
public class MyPageResponse {

    private final String name;
    private final String email;
    private final String role;
    private final String memberCategory;
    private final String sellerRole;
    private final String mbti;
    private final String job;
    private final String profileUrl;
    private final String phone;
    private final String createdAt;

}
