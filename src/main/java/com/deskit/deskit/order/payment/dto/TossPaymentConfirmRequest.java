package com.deskit.deskit.order.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TossPaymentConfirmRequest(
  @JsonProperty("paymentKey")
  @NotBlank
  String paymentKey,

  @JsonProperty("orderId")
  @NotBlank
  String orderId,

  @JsonProperty("amount")
  @NotNull
  Long amount
) {}
