package com.deskit.deskit.admin.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AdminAuthPendingResponse {

    private String name;
    private String email;
    private String phoneMasked;
}
