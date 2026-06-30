package com.networkscanner.backend.network.scan.impl;

import com.networkscanner.backend.config.SubnetScanExecutorConfig;
import com.networkscanner.backend.network.scan.model.ScanRunSource;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ScanSubnetExecutionPools {

  private final ThreadPoolTaskExecutor manualSubnetScanTaskExecutor;
  private final ThreadPoolTaskExecutor jobSubnetScanTaskExecutor;
  private final Semaphore manualSubnetScanConcurrencySemaphore;
  private final Semaphore jobSubnetScanConcurrencySemaphore;

  public ScanSubnetExecutionPools(
      @Qualifier(SubnetScanExecutorConfig.MANUAL_SUBNET_SCAN_TASK_EXECUTOR)
          ThreadPoolTaskExecutor manualSubnetScanTaskExecutor,
      @Qualifier(SubnetScanExecutorConfig.JOB_SUBNET_SCAN_TASK_EXECUTOR)
          ThreadPoolTaskExecutor jobSubnetScanTaskExecutor,
      @Qualifier(SubnetScanExecutorConfig.MANUAL_SUBNET_SCAN_CONCURRENCY_SEMAPHORE)
          Semaphore manualSubnetScanConcurrencySemaphore,
      @Qualifier(SubnetScanExecutorConfig.JOB_SUBNET_SCAN_CONCURRENCY_SEMAPHORE)
          Semaphore jobSubnetScanConcurrencySemaphore
  ) {
    this.manualSubnetScanTaskExecutor = manualSubnetScanTaskExecutor;
    this.jobSubnetScanTaskExecutor = jobSubnetScanTaskExecutor;
    this.manualSubnetScanConcurrencySemaphore = manualSubnetScanConcurrencySemaphore;
    this.jobSubnetScanConcurrencySemaphore = jobSubnetScanConcurrencySemaphore;
  }

  public ThreadPoolTaskExecutor taskExecutor(ScanRunSource source) {
    return source == ScanRunSource.JOB ? jobSubnetScanTaskExecutor : manualSubnetScanTaskExecutor;
  }

  public Semaphore concurrencySemaphore(ScanRunSource source) {
    return source == ScanRunSource.JOB
        ? jobSubnetScanConcurrencySemaphore
        : manualSubnetScanConcurrencySemaphore;
  }
}
