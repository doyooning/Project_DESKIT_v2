package com.deskit.deskit.order.payment.controller;

import com.deskit.deskit.order.payment.dto.TossPaymentConfirmRequest;
import com.deskit.deskit.order.payment.dto.TossPaymentConfirmResult;
import com.deskit.deskit.order.payment.service.TossPaymentService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/toss")
public class TossPaymentController {

  private final TossPaymentService tossPaymentService;

  public TossPaymentController(TossPaymentService tossPaymentService) {
    this.tossPaymentService = tossPaymentService;
  }

  @PostMapping("/confirm")
  public ResponseEntity<Map<String, Object>> confirmPayment(
    @Valid @RequestBody TossPaymentConfirmRequest request
  ) {
    TossPaymentConfirmResult result = tossPaymentService.confirmPayment(request);
    return ResponseEntity.status(result.statusCode()).body(result.body());
  }
}
