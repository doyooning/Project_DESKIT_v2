package com.deskit.deskit.account.address.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressCreateRequest(
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
