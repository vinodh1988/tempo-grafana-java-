package com.example.loadgen.controller;

import com.example.loadgen.service.ScenarioTriggerService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trigger")
public class ScenarioTriggerController {

  private final ScenarioTriggerService scenarioTriggerService;

  public ScenarioTriggerController(ScenarioTriggerService scenarioTriggerService) {
    this.scenarioTriggerService = scenarioTriggerService;
  }

  @GetMapping("/scenarios")
  public ResponseEntity<Map<String, Object>> listScenarios() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("scenarios", scenarioTriggerService.getScenarios());
    response.put("defaultScenario", "HAPPY");
    return ResponseEntity.ok(response);
  }

  @PostMapping("/run")
  public ResponseEntity<Map<String, Object>> run(
      @RequestParam(name = "scenario", required = false) String scenario) {
    return ResponseEntity.ok(scenarioTriggerService.runScenario(scenario));
  }
}
