package com.deskit.deskit.order.payment.dto;

import java.util.Map;

public record TossPaymentConfirmResult(
  int statusCode,
  Map<String, Object> body
) {}
