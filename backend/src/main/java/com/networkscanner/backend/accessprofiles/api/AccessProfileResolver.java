package com.networkscanner.backend.accessprofiles.api;

import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.network.scan.dto.DiscoveryProbeConfig;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import java.util.ArrayList;
import java.util.List;

public interface AccessProfileResolver {

  ScanRequest resolveScanRequest(ScanRequest request);

  MonitoringSnmpCredentials resolveSnmpCredentials(Long accessProfileId);

  MonitoringSnmpCredentials resolveSnmpCredentials(Long accessProfileId, String snmpProbeMethod);

  void validateProfileForMethods(Long accessProfileId, List<DiscoveryProbeConfig> probes);

  DiscoveryProbeConfig mergeProbeWithProfile(DiscoveryProbeConfig probe, AccessProfileEntity profile);
}
