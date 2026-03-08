package com.example.loadgen.controller;

import com.example.loadgen.service.LoadRunnerService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/load")
public class LoadController {

  private final LoadRunnerService loadRunnerService;

  public LoadController(LoadRunnerService loadRunnerService) {
    this.loadRunnerService = loadRunnerService;
  }

  @PostMapping("/start")
  public ResponseEntity<Map<String, Object>> start(
      @RequestParam(name = "rps", required = false) Integer rps) {
    return ResponseEntity.ok(loadRunnerService.startContinuous(rps));
  }

  @PostMapping("/stop")
  public ResponseEntity<Map<String, Object>> stop() {
    return ResponseEntity.ok(loadRunnerService.stopContinuous());
  }

  @PostMapping("/burst")
  public ResponseEntity<Map<String, Object>> burst(
      @RequestParam(name = "count", defaultValue = "100") int count) {
    return ResponseEntity.ok(loadRunnerService.runBurst(count));
  }

  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> status() {
    return ResponseEntity.ok(loadRunnerService.status());
  }
}
