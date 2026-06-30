package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.MonitoredDeviceIpLookup;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MonitoredDeviceIpLookupImpl implements MonitoredDeviceIpLookup {

  private final MonitoredDeviceRepository repository;

  public MonitoredDeviceIpLookupImpl(MonitoredDeviceRepository repository) {
    this.repository = repository;
  }

  @Override
  public Set<String> findMonitoredIpsIn(Collection<String> ips) {
    if (ips == null || ips.isEmpty()) {
      return Set.of();
    }
    return repository.findAllByIpIn(ips).stream()
        .map(e -> e.getIp())
        .collect(Collectors.toSet());
  }
}
