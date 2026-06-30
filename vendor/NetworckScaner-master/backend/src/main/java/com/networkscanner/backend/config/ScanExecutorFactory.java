package com.networkscanner.backend.config;

import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

final class ScanExecutorFactory {

  private ScanExecutorFactory() {}

  static ThreadPoolTaskExecutor taskExecutor(
      String beanName,
      String threadNamePrefix,
      int corePoolSize,
      int maxPoolSize,
      int queueCapacity
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setBeanName(beanName);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setCorePoolSize(Math.max(corePoolSize, 1));
    executor.setMaxPoolSize(Math.max(maxPoolSize, executor.getCorePoolSize()));
    executor.setQueueCapacity(Math.max(queueCapacity, 1));
    executor.setAllowCoreThreadTimeOut(false);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(120);
    executor.initialize();
    return executor;
  }

  static Semaphore fairSemaphore(int permits) {
    return new Semaphore(Math.max(permits, 1), true);
  }
}
