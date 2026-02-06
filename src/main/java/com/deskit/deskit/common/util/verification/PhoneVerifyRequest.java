package com.deskit.deskit.common.util.verification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneVerifyRequest {

    // 전화번호
    private String phoneNumber;

    // 인증 코드(6자리)
    private String code;
}
