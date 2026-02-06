package com.deskit.deskit.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ProductDetailUpdateRequest(
  @JsonProperty("detail_html")
  @NotNull
  String detailHtml
) {}
