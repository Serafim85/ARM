package com.networkscanner.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ScanJobsSchedulingConfig {

  @Bean
  public TaskScheduler scanJobsTaskScheduler(
      @Value("${scan.jobs.scheduler.pool-size:8}") int poolSize
  ) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(Math.max(poolSize, 1));
    scheduler.setThreadNamePrefix("scan-jobs-");
    scheduler.initialize();
    return scheduler;
  }
}

