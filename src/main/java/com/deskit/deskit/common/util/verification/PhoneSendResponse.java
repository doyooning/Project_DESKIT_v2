package com.deskit.deskit.common.util.verification;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PhoneSendResponse {

    // Response message for the phone send request.
    private String message;

    // Development-only code echo.
    private String code;
}
