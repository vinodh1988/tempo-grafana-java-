package com.example.order.client;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DownstreamClient {

  private final RestTemplate restTemplate;
  private final String paymentBaseUrl;
  private final String inventoryBaseUrl;

  public DownstreamClient(
      RestTemplate restTemplate,
      @Value("${service.payment-url}") String paymentBaseUrl,
      @Value("${service.inventory-url}") String inventoryBaseUrl) {
    this.restTemplate = restTemplate;
    this.paymentBaseUrl = paymentBaseUrl;
    this.inventoryBaseUrl = inventoryBaseUrl;
  }

  public Map<String, Object> reservePayment(String orderId) {
    return restTemplate.postForObject(
        paymentBaseUrl + "/payments/" + orderId,
        null,
        Map.class);
  }

  public Map<String, Object> reserveInventory(String orderId) {
    return restTemplate.postForObject(
        inventoryBaseUrl + "/inventory/reserve/" + orderId,
        null,
        Map.class);
  }
}
