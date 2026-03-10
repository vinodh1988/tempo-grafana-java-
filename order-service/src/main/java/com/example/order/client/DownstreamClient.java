package com.example.order.client;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DownstreamClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(DownstreamClient.class);

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
    try {
      return restTemplate.postForObject(
          paymentBaseUrl + "/payments/" + orderId,
          null,
          Map.class);
    } catch (Exception ex) {
      LOGGER.error("Payment downstream call failed for orderId={} reason={}", orderId, ex.getMessage());
      throw ex;
    }
  }

  public Map<String, Object> reserveInventory(String orderId) {
    try {
      return restTemplate.postForObject(
          inventoryBaseUrl + "/inventory/reserve/" + orderId,
          null,
          Map.class);
    } catch (Exception ex) {
      LOGGER.error("Inventory downstream call failed for orderId={} reason={}", orderId, ex.getMessage());
      throw ex;
    }
  }
}
