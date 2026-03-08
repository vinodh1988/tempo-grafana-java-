package com.example.order.controller;

import com.example.order.client.DownstreamClient;
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
@RequestMapping("/orders")
public class OrderController {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

  private final DownstreamClient downstreamClient;

  public OrderController(DownstreamClient downstreamClient) {
    this.downstreamClient = downstreamClient;
  }

  @PostMapping("/{orderId}")
  public ResponseEntity<Map<String, Object>> placeOrder(@PathVariable String orderId) {
    LOGGER.info("Received place-order request for orderId={}", orderId);

    Map<String, Object> paymentResponse = downstreamClient.reservePayment(orderId);
    Map<String, Object> inventoryResponse = downstreamClient.reserveInventory(orderId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("orderId", orderId);
    response.put("status", "PLACED");
    response.put("processedAt", Instant.now().toString());
    response.put("payment", paymentResponse);
    response.put("inventory", inventoryResponse);

    LOGGER.info("Order flow completed for orderId={}", orderId);
    return ResponseEntity.ok(response);
  }
}
