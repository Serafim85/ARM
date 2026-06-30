package com.networkscanner.backend.integration.api;

/**
 * Resolves the wiSLA integration {@code sourceSystem} value used in event payloads and Kafka keys.
 *
 * <p>Resolution order: configured property {@code app.integration.source-system} →
 * local host name → constant fallback ({@code networkscanner}).
 */
public interface SourceSystemProvider {
  /** Returns the resolved (cached) source system identifier. Never null/blank. */
  String getSourceSystem();
}
