package com.networkscanner.backend.integration.api;

/**
 * Resolves the NS runtime host name used as {@code instanceKey} for wiSLA incidents.
 */
public interface InstanceKeyHostProvider {
  /** Returns a non-blank host identifier for incident {@code instanceKey}. */
  String getHostName();
}
