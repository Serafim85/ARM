package com.networkscanner.backend.config;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Пулы для {@code SnmpScanServiceImpl.scan}: ручные и CRON-сканы изолированы на уровне потоков и семафоров.
 */
@Configuration
public class SubnetScanExecutorConfig {

  public static final String MANUAL_SUBNET_SCAN_TASK_EXECUTOR = "manualSubnetScanTaskExecutor";
  public static final String JOB_SUBNET_SCAN_TASK_EXECUTOR = "jobSubnetScanTaskExecutor";
  public static final String MANUAL_SUBNET_SCAN_CONCURRENCY_SEMAPHORE = "manualSubnetScanConcurrencySemaphore";
  public static final String JOB_SUBNET_SCAN_CONCURRENCY_SEMAPHORE = "jobSubnetScanConcurrencySemaphore";

  @Bean(name = MANUAL_SUBNET_SCAN_TASK_EXECUTOR)
  public ThreadPoolTaskExecutor manualSubnetScanTaskExecutor(
      @Value("${network.scan.manual.subnet-executor.core-pool-size:12}") int corePoolSize,
      @Value("${network.scan.manual.subnet-executor.max-pool-size:24}") int maxPoolSize,
      @Value("${network.scan.manual.subnet-executor.queue-capacity:1024}") int queueCapacity
  ) {
    return ScanExecutorFactory.taskExecutor(
        MANUAL_SUBNET_SCAN_TASK_EXECUTOR,
        "subnet-scan-manual-",
        corePoolSize,
        maxPoolSize,
        queueCapacity
    );
  }

  @Bean(name = JOB_SUBNET_SCAN_TASK_EXECUTOR)
  public ThreadPoolTaskExecutor jobSubnetScanTaskExecutor(
      @Value("${network.scan.job.subnet-executor.core-pool-size:16}") int corePoolSize,
      @Value("${network.scan.job.subnet-executor.max-pool-size:48}") int maxPoolSize,
      @Value("${network.scan.job.subnet-executor.queue-capacity:2048}") int queueCapacity
  ) {
    return ScanExecutorFactory.taskExecutor(
        JOB_SUBNET_SCAN_TASK_EXECUTOR,
        "subnet-scan-job-",
        corePoolSize,
        maxPoolSize,
        queueCapacity
    );
  }

  @Bean(name = MANUAL_SUBNET_SCAN_CONCURRENCY_SEMAPHORE)
  public Semaphore manualSubnetScanConcurrencySemaphore(
      @Value("${network.scan.manual.max-concurrent-subnet-scans:2}") int permits
  ) {
    return ScanExecutorFactory.fairSemaphore(permits);
  }

  @Bean(name = JOB_SUBNET_SCAN_CONCURRENCY_SEMAPHORE)
  public Semaphore jobSubnetScanConcurrencySemaphore(
      @Value("${network.scan.job.max-concurrent-subnet-scans:4}") int permits
  ) {
    return ScanExecutorFactory.fairSemaphore(permits);
  }
}
