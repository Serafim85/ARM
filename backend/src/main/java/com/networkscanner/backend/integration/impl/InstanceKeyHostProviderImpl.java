package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.api.InstanceKeyHostProvider;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InstanceKeyHostProviderImpl implements InstanceKeyHostProvider {

  private static final Logger log = LoggerFactory.getLogger(InstanceKeyHostProviderImpl.class);
  private static final String FALLBACK = "networkscanner";

  private volatile String resolved;

  @PostConstruct
  void resolve() {
    String host = readHostName();
    if (host != null && !host.isBlank()) {
      resolved = host;
      log.info("Wisla incident instanceKey host resolved from hostname: {}", resolved);
      return;
    }
    resolved = FALLBACK;
    log.warn("Failed to resolve hostname for incident instanceKey; using fallback: {}", resolved);
  }

  @Override
  public String getHostName() {
    if (resolved == null) {
      resolve();
    }
    return resolved;
  }

  private static String readHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException ex) {
      log.warn("Failed to read local host name for incident instanceKey: {}", ex.toString());
      return null;
    }
  }
}
