package com.example.loadgen.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class ScenarioTriggerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioTriggerService.class);

  private final RestTemplate restTemplate;
  private final String orderBaseUrl;

  public ScenarioTriggerService(
      RestTemplate restTemplate,
      @Value("${load.order-base-url}") String orderBaseUrl) {
    this.restTemplate = restTemplate;
    this.orderBaseUrl = orderBaseUrl;
  }

  public Map<String, Object> runScenario(String scenario) {
    String normalizedScenario = normalizeScenario(scenario);

    switch (normalizedScenario) {
      case "HAPPY":
        LOGGER.info("Triggering HAPPY scenario from UI");
        return callOrder(buildOrderId("HAPPY"), "Expected all services to succeed with INFO logs");
      case "WARN":
        LOGGER.warn("Triggering WARN scenario from UI");
        return callOrder(buildOrderId("WARN"), "Expected WARN logs in downstream services");
      case "ERROR":
        LOGGER.error("Triggering ERROR scenario from UI");
        return callOrder(buildOrderId("ERROR"), "Expected ERROR logs and 5xx response");
      case "BAD_ROUTE":
        LOGGER.warn("Triggering BAD_ROUTE scenario from UI");
        return callInvalidRoute();
      case "MIXED":
        LOGGER.info("Triggering MIXED scenario from UI");
        return runMixedScenario();
      default:
        LOGGER.warn("Unknown scenario requested: {}. Falling back to HAPPY", scenario);
        return callOrder(buildOrderId("HAPPY"), "Unknown scenario fallback to HAPPY");
    }
  }

  public List<Map<String, String>> getScenarios() {
    List<Map<String, String>> scenarios = new ArrayList<>();
    scenarios.add(scenario("HAPPY", "Valid request. Generates INFO logs and a full trace."));
    scenarios.add(scenario("WARN", "Valid request using managed warning input. Generates WARN + INFO logs."));
    scenarios.add(scenario("ERROR", "Managed invalid input. Triggers downstream ERROR logs and 5xx."));
    scenarios.add(scenario("BAD_ROUTE", "Calls an invalid route to intentionally produce client-side warning/error signals."));
    scenarios.add(scenario("MIXED", "Runs HAPPY, WARN, ERROR, and BAD_ROUTE in sequence."));
    return scenarios;
  }

  private Map<String, Object> runMixedScenario() {
    List<String> sequence = Arrays.asList("HAPPY", "WARN", "ERROR", "BAD_ROUTE");
    List<Map<String, Object>> results = new ArrayList<>();

    for (String scenario : sequence) {
      if ("BAD_ROUTE".equals(scenario)) {
        results.add(callInvalidRoute());
      } else {
        results.add(callOrder(buildOrderId(scenario), "MIXED run component: " + scenario));
      }
    }

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("scenario", "MIXED");
    response.put("ran", sequence);
    response.put("results", results);
    response.put("at", Instant.now().toString());
    return response;
  }

  private Map<String, Object> callOrder(String orderId, String expectedOutcome) {
    String targetUrl = orderBaseUrl + "/orders/" + orderId;

    try {
      ResponseEntity<Map> response = restTemplate.postForEntity(targetUrl, null, Map.class);

      Map<String, Object> result = baseResult("ORDER_FLOW", targetUrl, expectedOutcome);
      result.put("orderId", orderId);
      result.put("httpStatus", response.getStatusCode().value());
      result.put("success", response.getStatusCode().is2xxSuccessful());
      result.put("response", response.getBody());
      return result;
    } catch (HttpStatusCodeException ex) {
      Map<String, Object> result = baseResult("ORDER_FLOW", targetUrl, expectedOutcome);
      result.put("orderId", orderId);
      result.put("httpStatus", ex.getRawStatusCode());
      result.put("success", false);
      result.put("error", ex.getResponseBodyAsString());
      return result;
    } catch (Exception ex) {
      Map<String, Object> result = baseResult("ORDER_FLOW", targetUrl, expectedOutcome);
      result.put("orderId", orderId);
      result.put("success", false);
      result.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
      return result;
    }
  }

  private Map<String, Object> callInvalidRoute() {
    String targetUrl = orderBaseUrl + "/orders";
    String expectedOutcome = "Expected 4xx/5xx due to invalid route invocation";

    try {
      ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, null, String.class);
      Map<String, Object> result = baseResult("BAD_ROUTE", targetUrl, expectedOutcome);
      result.put("httpStatus", response.getStatusCode().value());
      result.put("success", response.getStatusCode().is2xxSuccessful());
      result.put("response", response.getBody());
      return result;
    } catch (HttpStatusCodeException ex) {
      Map<String, Object> result = baseResult("BAD_ROUTE", targetUrl, expectedOutcome);
      result.put("httpStatus", ex.getRawStatusCode());
      result.put("success", false);
      result.put("error", ex.getResponseBodyAsString());
      return result;
    } catch (Exception ex) {
      Map<String, Object> result = baseResult("BAD_ROUTE", targetUrl, expectedOutcome);
      result.put("success", false);
      result.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
      return result;
    }
  }

  private Map<String, Object> baseResult(String flow, String targetUrl, String expectedOutcome) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("flow", flow);
    result.put("targetUrl", targetUrl);
    result.put("expectedOutcome", expectedOutcome);
    result.put("at", Instant.now().toString());
    return result;
  }

  private Map<String, String> scenario(String name, String description) {
    Map<String, String> scenario = new LinkedHashMap<>();
    scenario.put("name", name);
    scenario.put("description", description);
    return scenario;
  }

  private String buildOrderId(String tag) {
    return "UI-" + tag + "-" + UUID.randomUUID();
  }

  private String normalizeScenario(String scenario) {
    if (scenario == null || scenario.trim().isEmpty()) {
      return "HAPPY";
    }
    return scenario.trim().toUpperCase();
  }
}
