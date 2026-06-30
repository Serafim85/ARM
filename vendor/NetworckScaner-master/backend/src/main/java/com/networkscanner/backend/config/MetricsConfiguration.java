package com.networkscanner.backend.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

  /**
   * Enables {@link io.micrometer.core.instrument.MeterRegistry} injection when no other registry
   * (e.g. Prometheus) is present.
   */
  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  MeterRegistry simpleMeterRegistry() {
    return new SimpleMeterRegistry();
  }
}
