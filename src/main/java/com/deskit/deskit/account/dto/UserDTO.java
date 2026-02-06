package com.deskit.deskit.account.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserDTO {

    // 권한
    private String role;

    // 이름
    private String name;

    // provider + providerId
    private String username;

    // Email
    private String email;

    // 프로필 사진 주소
    private String profileUrl;

    // 신규 가입인지 확인하는 플래그
    private boolean newUser;
}
