package com.networkscanner.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Пулы координаторов {@code ScanRunService}: ручные и автосканы не делят потоки и очередь.
 */
@Configuration
public class ScanRunExecutorConfig {

  public static final String MANUAL_SCAN_RUN_TASK_EXECUTOR = "manualScanRunTaskExecutor";
  public static final String JOB_SCAN_RUN_TASK_EXECUTOR = "jobScanRunTaskExecutor";

  @Bean(name = MANUAL_SCAN_RUN_TASK_EXECUTOR)
  public ThreadPoolTaskExecutor manualScanRunTaskExecutor(
      @Value("${network.scan.manual.run-executor.core-pool-size:2}") int corePoolSize,
      @Value("${network.scan.manual.run-executor.max-pool-size:4}") int maxPoolSize,
      @Value("${network.scan.manual.run-executor.queue-capacity:32}") int queueCapacity
  ) {
    return ScanExecutorFactory.taskExecutor(
        MANUAL_SCAN_RUN_TASK_EXECUTOR,
        "scan-run-manual-",
        corePoolSize,
        maxPoolSize,
        queueCapacity
    );
  }

  @Bean(name = JOB_SCAN_RUN_TASK_EXECUTOR)
  public ThreadPoolTaskExecutor jobScanRunTaskExecutor(
      @Value("${network.scan.job.run-executor.core-pool-size:4}") int corePoolSize,
      @Value("${network.scan.job.run-executor.max-pool-size:8}") int maxPoolSize,
      @Value("${network.scan.job.run-executor.queue-capacity:64}") int queueCapacity
  ) {
    return ScanExecutorFactory.taskExecutor(
        JOB_SCAN_RUN_TASK_EXECUTOR,
        "scan-run-job-",
        corePoolSize,
        maxPoolSize,
        queueCapacity
    );
  }
}
