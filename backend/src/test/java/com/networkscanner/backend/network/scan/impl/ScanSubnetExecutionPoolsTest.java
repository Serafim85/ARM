package com.networkscanner.backend.network.scan.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.networkscanner.backend.network.scan.model.ScanRunSource;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ScanSubnetExecutionPoolsTest {

  private ThreadPoolTaskExecutor manualExecutor;
  private ThreadPoolTaskExecutor jobExecutor;
  private Semaphore manualSemaphore;
  private Semaphore jobSemaphore;
  private ScanSubnetExecutionPools pools;

  @BeforeEach
  void setUp() {
    manualExecutor = createExecutor("manual");
    jobExecutor = createExecutor("job");
    manualSemaphore = new Semaphore(2, true);
    jobSemaphore = new Semaphore(4, true);
    pools = new ScanSubnetExecutionPools(
        manualExecutor,
        jobExecutor,
        manualSemaphore,
        jobSemaphore
    );
  }

  @Test
  void routesManualAndJobResourcesSeparately() {
    assertThat(pools.taskExecutor(ScanRunSource.MANUAL)).isSameAs(manualExecutor);
    assertThat(pools.taskExecutor(ScanRunSource.JOB)).isSameAs(jobExecutor);
    assertThat(pools.concurrencySemaphore(ScanRunSource.MANUAL)).isSameAs(manualSemaphore);
    assertThat(pools.concurrencySemaphore(ScanRunSource.JOB)).isSameAs(jobSemaphore);
  }

  private static ThreadPoolTaskExecutor createExecutor(String name) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setBeanName(name);
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    executor.initialize();
    return executor;
  }
}
