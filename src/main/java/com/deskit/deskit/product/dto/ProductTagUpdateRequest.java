package com.deskit.deskit.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductTagUpdateRequest(
  @JsonProperty("tag_ids")
  @NotNull
  List<Long> tagIds
) {}
