package com.deskit.deskit.livehost.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class VodStatusRequest {
    @NotBlank
    private String status;
}
