package com.networkscanner.backend.network.scan.impl;

import com.networkscanner.backend.network.scan.api.IcmpProbeResult;
import com.networkscanner.backend.network.scan.api.IcmpProbeService;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IcmpProbeServiceImpl implements IcmpProbeService {

  private final int attempts;
  private final int timeoutMs;

  public IcmpProbeServiceImpl(
      @Value("${monitoring.icmp.attempts:3}") int attempts,
      @Value("${monitoring.icmp.timeout-ms:1000}") int timeoutMs
  ) {
    this.attempts = Math.max(attempts, 1);
    this.timeoutMs = Math.max(timeoutMs, 1);
  }

  @Override
  public IcmpProbeResult probe(String ip) {
    InetAddress address;
    try {
      address = InetAddress.getByName(ip);
    } catch (IOException exception) {
      return failureResult();
    }

    int successCount = 0;
    long totalLatencyNanos = 0L;
    for (int attempt = 0; attempt < attempts; attempt++) {
      long startedAt = System.nanoTime();
      boolean reachable = false;
      try {
        // InetAddress#isReachable does not guarantee a raw ICMP socket on every OS/JVM.
        reachable = address.isReachable(timeoutMs);
      } catch (IOException ignored) {
        reachable = false;
      }
      if (reachable) {
        successCount++;
        totalLatencyNanos += Math.max(System.nanoTime() - startedAt, 0L);
      }
    }

    double packetLossPercent = ((double) (attempts - successCount) * 100.0d) / attempts;
    double averageResponseSeconds = successCount == 0
        ? timeoutMs / 1000.0d
        : ((double) totalLatencyNanos / successCount) / TimeUnit.SECONDS.toNanos(1);
    return new IcmpProbeResult(
        attempts,
        successCount,
        packetLossPercent,
        averageResponseSeconds,
        successCount > 0
    );
  }

  private IcmpProbeResult failureResult() {
    return new IcmpProbeResult(
        attempts,
        0,
        100.0d,
        timeoutMs / 1000.0d,
        false
    );
  }
}
