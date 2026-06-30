package com.networkscanner.backend.network.scan.api;

import com.networkscanner.backend.network.scan.model.ScanRunSource;
import java.util.concurrent.atomic.AtomicBoolean;

public record ScanRunContext(
    long runId,
    ScanRunSource source,
    AtomicBoolean stopRequested,
    ScanProgressListener progressListener
) {

  public ScanRunContext {
    if (source == null) {
      source = ScanRunSource.MANUAL;
    }
  }

  public static ScanRunContext noop() {
    return new ScanRunContext(0L, ScanRunSource.MANUAL, new AtomicBoolean(false), (scanned, total) -> {});
  }

  public boolean isStopRequested() {
    return stopRequested.get();
  }
}
