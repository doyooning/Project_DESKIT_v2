package com.deskit.deskit.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record OrderCancelRequest(
  @JsonProperty("reason")
  @NotBlank
  String reason
) {}
