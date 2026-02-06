package com.deskit.deskit.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductImageUploadResponse(
  @JsonProperty("url")
  String url,

  @JsonProperty("key")
  String key
) {}
