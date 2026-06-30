package com.networkscanner.backend.network.scanjobs.api;

public interface ScanJobScheduler {

  void upsert(long jobId);

  void remove(long jobId);

  void scheduleAllEnabled();
}

