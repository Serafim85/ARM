package com.networkscanner.backend.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link Executors#newFixedThreadPool(int)} uses generic {@code pool-N-thread-M} names (e.g. {@code pool-2},
 * {@code pool-3} in {@code jcmd} thread dumps). This factory assigns an explicit prefix per pool so
 * ownership is visible in production diagnostics.
 */
public final class NamedExecutors {

  private NamedExecutors() {
  }

  public static ExecutorService newFixedThreadPool(int threads, String namePrefix) {
    int n = Math.max(threads, 1);
    AtomicInteger seq = new AtomicInteger(1);
    ThreadFactory factory = runnable -> {
      Thread thread = new Thread(runnable, namePrefix + seq.getAndIncrement());
      thread.setDaemon(false);
      return thread;
    };
    return Executors.newFixedThreadPool(n, factory);
  }
}
