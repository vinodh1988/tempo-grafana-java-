package com.example.loadgen;

import com.example.loadgen.service.LoadRunnerService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class LoadGeneratorServiceApplication {

  private final LoadRunnerService loadRunnerService;

  public LoadGeneratorServiceApplication(LoadRunnerService loadRunnerService) {
    this.loadRunnerService = loadRunnerService;
  }

  public static void main(String[] args) {
    SpringApplication.run(LoadGeneratorServiceApplication.class, args);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void autoStartLoadAfterBoot() {
    loadRunnerService.startAutoIfEnabled();
  }
}
