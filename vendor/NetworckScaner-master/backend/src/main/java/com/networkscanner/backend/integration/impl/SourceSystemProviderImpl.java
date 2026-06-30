package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Default {@link SourceSystemProvider} implementation: prefers configured value, falls back to local hostname,
 * then to a hardcoded constant. The result is resolved once at startup and cached.
 */
@Component
public class SourceSystemProviderImpl implements SourceSystemProvider {

  private static final Logger log = LoggerFactory.getLogger(SourceSystemProviderImpl.class);
  private static final String FALLBACK = "networkscanner";

  private final String configuredValue;
  private volatile String resolved;

  public SourceSystemProviderImpl(
      @Value("${app.integration.source-system:}") String configuredValue
  ) {
    this.configuredValue = configuredValue;
  }

  @PostConstruct
  void resolve() {
    if (configuredValue != null && !configuredValue.isBlank()) {
      resolved = configuredValue.trim();
      log.info("Wisla sourceSystem resolved from property: {}", resolved);
      return;
    }
    String host = readHostName();
    if (host != null && !host.isBlank()) {
      resolved = host;
      log.info("Wisla sourceSystem property empty; resolved from hostname: {}", resolved);
      return;
    }
    resolved = FALLBACK;
    log.warn("Wisla sourceSystem property empty and hostname unavailable; using fallback: {}", resolved);
  }

  @Override
  public String getSourceSystem() {
    if (resolved == null) {
      resolve();
    }
    return resolved;
  }

  private static String readHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ex) {
      log.warn("Failed to read local host name for sourceSystem fallback: {}", ex.toString());
      return null;
    }
  }
}
