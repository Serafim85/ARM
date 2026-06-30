package com.networkscanner.backend.monitoring.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class TriggerEngineConfiguration {

  @Value("${monitoring.trigger-engine.extended-functions-enabled:true}")
  private boolean extendedFunctionsEnabled;

  @PostConstruct
  void configure() {
    TriggerEvaluationSupport.configureEngine(extendedFunctionsEnabled);
  }
}
