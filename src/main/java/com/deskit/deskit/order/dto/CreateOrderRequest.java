package com.deskit.deskit.order.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
  @JsonProperty("items")
  @NotNull
  @Size(min = 1)
  List<CreateOrderItemRequest> items,

  @JsonProperty("receiver")
  @NotBlank
  @Size(max = 20)
  String receiver,

  @JsonProperty("postcode")
  @NotBlank
  @Pattern(regexp = "\\d{5}")
  String postcode,

  @JsonProperty("addr_detail")
  @NotBlank
  @Size(max = 255)
  String addrDetail,

  @JsonProperty("is_default")
  Boolean isDefault
) {}
