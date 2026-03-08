package com.example.payment.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

  private static final Logger LOGGER = LoggerFactory.getLogger(PaymentController.class);

  @PostMapping("/{orderId}")
  public ResponseEntity<Map<String, Object>> createPayment(@PathVariable String orderId) {
    LOGGER.info("Processing payment for orderId={}", orderId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", orderId);
    response.put("paymentStatus", "CONFIRMED");
    response.put("processedAt", Instant.now().toString());

    LOGGER.info("Payment completed for orderId={}", orderId);
    return ResponseEntity.ok(response);
  }
}
