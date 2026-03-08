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
    LOGGER.info("Reserving stock for orderId={}", orderId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", orderId);
    response.put("inventoryStatus", "RESERVED");
    response.put("processedAt", Instant.now().toString());

    LOGGER.info("Inventory reserved for orderId={}", orderId);
    return ResponseEntity.ok(response);
  }
}
