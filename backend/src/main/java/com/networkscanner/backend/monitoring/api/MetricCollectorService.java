package com.networkscanner.backend.monitoring.api;

public interface MetricCollectorService {

  void collectAll();

  void shutdown();
}
