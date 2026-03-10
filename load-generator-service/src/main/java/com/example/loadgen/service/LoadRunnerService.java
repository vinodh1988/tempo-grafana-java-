package com.example.loadgen.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LoadRunnerService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoadRunnerService.class);

  private final RestTemplate restTemplate;
  private final String orderBaseUrl;
  private final int defaultRps;
  private final int maxRps;
  private final int burstMaxCount;
  private final boolean autoEnabled;
  private final long autoStartDelayMs;
  private final ScheduledExecutorService executor;
  private final AtomicLong sentCount;
  private final AtomicLong failedCount;
  private final AtomicReference<String> lastOrderId;
  private final AtomicReference<String> lastResult;
  private final AtomicReference<String> lastFailureReason;

  private volatile ScheduledFuture<?> activeTask;
  private volatile int activeRps;

  public LoadRunnerService(
      RestTemplate restTemplate,
      @Value("${load.order-base-url}") String orderBaseUrl,
      @Value("${load.default-rps}") int defaultRps,
      @Value("${load.max-rps}") int maxRps,
      @Value("${load.burst-max-count}") int burstMaxCount,
      @Value("${load.auto-enabled}") boolean autoEnabled,
      @Value("${load.auto-start-delay-ms}") long autoStartDelayMs) {
    this.restTemplate = restTemplate;
    this.orderBaseUrl = orderBaseUrl;
    this.defaultRps = defaultRps;
    this.maxRps = Math.max(1, maxRps);
    this.burstMaxCount = Math.max(1, burstMaxCount);
    this.autoEnabled = autoEnabled;
    this.autoStartDelayMs = Math.max(0L, autoStartDelayMs);
    this.executor = Executors.newSingleThreadScheduledExecutor();
    this.sentCount = new AtomicLong(0);
    this.failedCount = new AtomicLong(0);
    this.lastOrderId = new AtomicReference<>("-");
    this.lastResult = new AtomicReference<>("-");
    this.lastFailureReason = new AtomicReference<>("-");
    this.activeRps = 0;
  }

  public void startAutoIfEnabled() {
    if (!autoEnabled) {
      LOGGER.info("Automatic background load is disabled");
      return;
    }

    CompletableFuture.runAsync(
        () -> {
          try {
            if (autoStartDelayMs > 0) {
              Thread.sleep(autoStartDelayMs);
            }
            startContinuous(defaultRps);
            LOGGER.info("Automatic background load started at rps={}", defaultRps);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Auto-start load was interrupted");
          }
        });
  }

  public synchronized Map<String, Object> startContinuous(Integer requestedRps) {
    int rps = sanitizeRps(requestedRps == null ? defaultRps : requestedRps);
    // Ensure only one scheduled load loop is active at a time.
    stopContinuous();

    long periodMs = Math.max(1, 1000L / rps);
    activeTask = executor.scheduleAtFixedRate(this::sendSingleRequest, 0, periodMs, TimeUnit.MILLISECONDS);
    activeRps = rps;

    LOGGER.info("Started continuous load at rps={}", rps);
    return status("started");
  }

  public synchronized Map<String, Object> stopContinuous() {
    if (activeTask != null) {
      activeTask.cancel(true);
      activeTask = null;
      LOGGER.info("Stopped continuous load");
    }
    activeRps = 0;
    return status("stopped");
  }

  public Map<String, Object> runBurst(int count) {
    int safeCount = Math.max(1, Math.min(count, burstMaxCount));
    for (int i = 0; i < safeCount; i++) {
      sendSingleRequest();
    }
    LOGGER.info("Executed burst load count={}", safeCount);
    return status("burst-completed");
  }

  public Map<String, Object> status(String state) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("state", state);
    response.put("active", activeTask != null && !activeTask.isCancelled());
    response.put("activeRps", activeRps);
    response.put("targetOrderUrl", orderBaseUrl + "/orders/{orderId}");
    response.put("sent", sentCount.get());
    response.put("failed", failedCount.get());
    response.put("lastOrderId", lastOrderId.get());
    response.put("lastResult", lastResult.get());
    response.put("lastFailureReason", lastFailureReason.get());
    response.put("at", Instant.now().toString());
    return response;
  }

  public Map<String, Object> status() {
    return status("status");
  }

  private int sanitizeRps(int rps) {
    return Math.max(1, Math.min(rps, maxRps));
  }

  private void sendSingleRequest() {
    String orderId = "GEN-" + UUID.randomUUID();
    lastOrderId.set(orderId);
    try {
      ResponseEntity<Map> response =
          restTemplate.postForEntity(orderBaseUrl + "/orders/" + orderId, null, Map.class);
      sentCount.incrementAndGet();
      lastResult.set("HTTP " + response.getStatusCode().value());
      lastFailureReason.set("-");
    } catch (Exception ex) {
      failedCount.incrementAndGet();
      String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
      lastResult.set("FAILED");
      lastFailureReason.set(reason);
      LOGGER.warn("Load request failed for orderId={} reason={}", orderId, reason);
    }
  }
}
