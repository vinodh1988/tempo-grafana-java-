package com.example.inventory.controller;

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
@RequestMapping("/inventory")
public class InventoryController {

  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryController.class);

  @PostMapping("/reserve/{orderId}")
  public ResponseEntity<Map<String, Object>> reserve(@PathVariable String orderId) {
    String normalizedOrderId = orderId.toUpperCase();

    if (normalizedOrderId.contains("ERROR")) {
      LOGGER.error("Simulated inventory reservation failure for orderId={}", orderId);

      Map<String, Object> errorResponse = new LinkedHashMap<>();
      errorResponse.put("orderId", orderId);
      errorResponse.put("inventoryStatus", "FAILED");
      errorResponse.put("reason", "Simulated inventory error path");
      errorResponse.put("processedAt", Instant.now().toString());
      return ResponseEntity.status(500).body(errorResponse);
    }

    if (normalizedOrderId.contains("WARN")) {
      LOGGER.warn("Simulated warning during inventory reservation for orderId={}", orderId);
    }

    LOGGER.info("Reserving stock for orderId={}", orderId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", orderId);
    response.put("inventoryStatus", normalizedOrderId.contains("WARN") ? "RESERVED_WITH_WARNING" : "RESERVED");
    response.put("processedAt", Instant.now().toString());

    LOGGER.info("Inventory reserved for orderId={}", orderId);
    return ResponseEntity.ok(response);
  }
}
