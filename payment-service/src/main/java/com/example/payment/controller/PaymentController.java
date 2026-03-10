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
    String normalizedOrderId = orderId.toUpperCase();

    if (normalizedOrderId.contains("ERROR")) {
      LOGGER.error("Simulated payment processing error for orderId={}", orderId);

      Map<String, Object> errorResponse = new LinkedHashMap<>();
      errorResponse.put("orderId", orderId);
      errorResponse.put("paymentStatus", "FAILED");
      errorResponse.put("reason", "Simulated payment error path");
      errorResponse.put("processedAt", Instant.now().toString());
      return ResponseEntity.status(500).body(errorResponse);
    }

    if (normalizedOrderId.contains("WARN")) {
      LOGGER.warn("Simulated warning during payment for orderId={}", orderId);
    }

    LOGGER.info("Processing payment for orderId={}", orderId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", orderId);
    response.put("paymentStatus", normalizedOrderId.contains("WARN") ? "CONFIRMED_WITH_WARNING" : "CONFIRMED");
    response.put("processedAt", Instant.now().toString());

    LOGGER.info("Payment completed for orderId={}", orderId);
    return ResponseEntity.ok(response);
  }
}
