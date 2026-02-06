package com.deskit.deskit.common.util.verification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneSendRequest {

    // Phone number to receive verification code.
    private String phoneNumber;
}
