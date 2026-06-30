package com.networkscanner.backend.accessprofiles.impl;

import com.networkscanner.backend.accessprofiles.api.AccessProfileResolver;
import com.networkscanner.backend.accessprofiles.api.AccessProfileService;
import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.network.scan.dto.DiscoveryProbeConfig;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessProfileResolverImpl implements AccessProfileResolver {

  private final AccessProfileService accessProfileService;

  public AccessProfileResolverImpl(AccessProfileService accessProfileService) {
    this.accessProfileService = accessProfileService;
  }

  @Override
  public ScanRequest resolveScanRequest(ScanRequest request) {
    if (request == null || request.accessProfileId() == null) {
      return request;
    }
    AccessProfileEntity profile = accessProfileService.requireEntity(request.accessProfileId());
    validateProfileForMethods(request.accessProfileId(), request.effectiveProbes());
    List<DiscoveryProbeConfig> resolvedProbes = new ArrayList<>();
    for (DiscoveryProbeConfig probe : request.effectiveProbes()) {
      resolvedProbes.add(mergeProbeWithProfile(probe, profile));
    }
    return new ScanRequest(
        request.subnetRange(),
        request.scanMode(),
        request.snmpVersion(),
        request.port(),
        request.timeout(),
        request.retries(),
        request.community(),
        request.securityUsername(),
        request.authProtocol(),
        request.authPassword(),
        request.privacyProtocol(),
        request.privacyPassword(),
        request.accessProfileId(),
        resolvedProbes
    );
  }

  @Override
  public MonitoringSnmpCredentials resolveSnmpCredentials(Long accessProfileId) {
    return resolveSnmpCredentials(accessProfileId, null);
  }

  @Override
  public MonitoringSnmpCredentials resolveSnmpCredentials(Long accessProfileId, String snmpProbeMethod) {
    if (accessProfileId == null) {
      return null;
    }
    AccessProfileEntity profile = accessProfileService.requireEntity(accessProfileId);
    String method = snmpProbeMethod == null ? null : normalizeMethod(snmpProbeMethod);
    if (method == null) {
      if (profile.isSnmpV3Enabled()) {
        method = "SNMP_V3";
      } else if (profile.isSnmpV2Enabled()) {
        method = "SNMP_V2";
      } else if (profile.isSnmpV1Enabled()) {
        method = "SNMP_V1";
      }
    }
    if ("SNMP_V3".equals(method) && profile.isSnmpV3Enabled()) {
      return new MonitoringSnmpCredentials(
          "v3",
          null,
          profile.getSnmpV3SecurityUsername(),
          firstNonBlank(profile.getSnmpV3AuthProtocol(), "SHA"),
          profile.getSnmpV3AuthPassword(),
          firstNonBlank(profile.getSnmpV3PrivacyProtocol(), "AES"),
          profile.getSnmpV3PrivacyPassword()
      );
    }
    if ("SNMP_V1".equals(method) && profile.isSnmpV1Enabled()) {
      return new MonitoringSnmpCredentials(
          "v1",
          firstNonBlank(profile.getSnmpV1Community(), "public"),
          null,
          null,
          null,
          null,
          null
      );
    }
    if ("SNMP_V2".equals(method) && profile.isSnmpV2Enabled()) {
      return new MonitoringSnmpCredentials(
          "v2c",
          firstNonBlank(profile.getSnmpV2Community(), "public"),
          null,
          null,
          null,
          null,
          null
      );
    }
    return null;
  }

  @Override
  public void validateProfileForMethods(Long accessProfileId, List<DiscoveryProbeConfig> probes) {
    if (accessProfileId == null || probes == null || probes.isEmpty()) {
      return;
    }
    AccessProfileEntity profile = accessProfileService.requireEntity(accessProfileId);
    for (DiscoveryProbeConfig probe : probes) {
      String method = normalizeMethod(probe.method());
      if (!isSnmpMethod(method)) {
        continue;
      }
      if (profileCoversSnmpMethod(profile, method)) {
        continue;
      }
      if (!hasInlineSnmpCredentials(probe)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Выбранный профиль не содержит настроек " + snmpLabel(method) + ". Укажите параметры SNMP вручную."
        );
      }
    }
  }

  @Override
  public DiscoveryProbeConfig mergeProbeWithProfile(DiscoveryProbeConfig probe, AccessProfileEntity profile) {
    if (probe == null || profile == null) {
      return probe;
    }
    String method = normalizeMethod(probe.method());
    Integer port = probe.port();
    if ("SNMP_V1".equals(method) && profile.isSnmpV1Enabled()) {
      return new DiscoveryProbeConfig(
          probe.method(),
          defaultPort(profile.getSnmpV1Port(), port != null && port > 0 ? port : 161),
          firstNonBlank(profile.getSnmpV1Community(), "public"),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null
      );
    }
    if ("SNMP_V2".equals(method) && profile.isSnmpV2Enabled()) {
      return new DiscoveryProbeConfig(
          probe.method(),
          defaultPort(profile.getSnmpV2Port(), port != null && port > 0 ? port : 161),
          firstNonBlank(profile.getSnmpV2Community(), "public"),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null
      );
    }
    if ("SNMP_V3".equals(method) && profile.isSnmpV3Enabled()) {
      return new DiscoveryProbeConfig(
          probe.method(),
          defaultPort(profile.getSnmpV3Port(), port != null && port > 0 ? port : 161),
          null,
          profile.getSnmpV3SecurityUsername(),
          firstNonBlank(profile.getSnmpV3AuthProtocol(), "SHA"),
          profile.getSnmpV3AuthPassword(),
          firstNonBlank(profile.getSnmpV3PrivacyProtocol(), "AES"),
          profile.getSnmpV3PrivacyPassword(),
          null,
          null,
          null,
          null,
          null,
          null,
          null
      );
    }
    if ("SSH".equals(method) && profile.isSshEnabled()) {
      int sshPort = port != null && port > 0 ? port : defaultPort(profile.getSshPort(), 22);
      return new DiscoveryProbeConfig(
          probe.method(),
          sshPort,
          null,
          null,
          null,
          null,
          null,
          null,
          profile.getSshUsername(),
          profile.getSshPassword(),
          profile.getSshPrivateKeyPem(),
          profile.getSshPassphrase(),
          null,
          null,
          null
      );
    }
    if ("HTTPS".equals(method) && profile.isHttpsEnabled()) {
      int httpsPort = port != null && port > 0 ? port : defaultPort(profile.getHttpsPort(), 443);
      return new DiscoveryProbeConfig(
          probe.method(),
          httpsPort,
          null,
          null,
          null,
          null,
          null,
          null,
          profile.getHttpsUsername(),
          profile.getHttpsPassword(),
          null,
          null,
          profile.getHttpsClientCertPem(),
          profile.getHttpsClientKeyPem(),
          profile.isHttpsInsecureSkipVerify()
      );
    }
    return probe;
  }

  private static boolean profileCoversSnmpMethod(AccessProfileEntity profile, String method) {
    return switch (method) {
      case "SNMP_V1" -> profile.isSnmpV1Enabled();
      case "SNMP_V2" -> profile.isSnmpV2Enabled();
      case "SNMP_V3" -> profile.isSnmpV3Enabled();
      default -> false;
    };
  }

  private static String snmpLabel(String method) {
    return switch (method) {
      case "SNMP_V1" -> "SNMP v1";
      case "SNMP_V2" -> "SNMP v2c";
      case "SNMP_V3" -> "SNMP v3";
      default -> "SNMP";
    };
  }

  private boolean hasInlineSnmpCredentials(DiscoveryProbeConfig probe) {
    if (probe == null) {
      return false;
    }
    String method = normalizeMethod(probe.method());
    if ("SNMP_V3".equals(method)) {
      return probe.securityUsername() != null && !probe.securityUsername().isBlank();
    }
    return probe.community() != null && !probe.community().isBlank();
  }

  private static boolean isSnmpMethod(String method) {
    String normalized = normalizeMethod(method);
    return normalized.startsWith("SNMP");
  }

  private static String normalizeMethod(String method) {
    return method == null ? "" : method.trim().toUpperCase();
  }

  private static int defaultPort(Integer profilePort, int fallback) {
    return profilePort != null && profilePort > 0 ? profilePort : fallback;
  }

  private static String firstNonBlank(String value, String fallback) {
    if (value != null && !value.isBlank()) {
      return value.trim();
    }
    return fallback;
  }
}
