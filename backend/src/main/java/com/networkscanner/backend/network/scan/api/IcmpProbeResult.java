package com.networkscanner.backend.network.scan.api;

public record IcmpProbeResult(
    int attempts,
    int successfulAttempts,
    double packetLossPercent,
    double averageResponseSeconds,
    boolean reachable
) {
}
