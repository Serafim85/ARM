package com.networkscanner.backend.network.scan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ScanRequest(
    @NotBlank String subnetRange,
    String scanMode,
    String snmpVersion,
    @Min(1) @Max(65535) int port,
    @Min(100) @Max(60000) int timeout,
    @Min(0) @Max(10) int retries,
    String community,
    String securityUsername,
    String authProtocol,
    String authPassword,
    String privacyProtocol,
    String privacyPassword,
    Long accessProfileId,
    @Valid List<DiscoveryProbeConfig> probes
) {

  public ScanRequest(
      String subnetRange,
      String scanMode,
      String snmpVersion,
      int port,
      int timeout,
      int retries,
      String community,
      String securityUsername,
      String authProtocol,
      String authPassword,
      String privacyProtocol,
      String privacyPassword,
      List<DiscoveryProbeConfig> probes
  ) {
    this(
        subnetRange,
        scanMode,
        snmpVersion,
        port,
        timeout,
        retries,
        community,
        securityUsername,
        authProtocol,
        authPassword,
        privacyProtocol,
        privacyPassword,
        null,
        probes
    );
  }

  @AssertTrue(message = "Укажите хотя бы один метод обнаружения (probes или scanMode).")
  public boolean hasDiscoveryMethod() {
    if (probes != null && !probes.isEmpty()) {
      return true;
    }
    return scanMode != null && !scanMode.isBlank();
  }

  public List<DiscoveryProbeConfig> effectiveProbes() {
    if (probes != null && !probes.isEmpty()) {
      return probes;
    }
    if (scanMode == null || scanMode.isBlank()) {
      return List.of();
    }
    return List.of(new DiscoveryProbeConfig(
        normalizeMethod(scanMode),
        port > 0 ? port : null,
        community,
        securityUsername,
        authProtocol,
        authPassword,
        privacyProtocol,
        privacyPassword
    ));
  }

  public DiscoveryProbeConfig preferredSnmpProbe() {
    DiscoveryProbeConfig v3 = null;
    DiscoveryProbeConfig v2 = null;
    DiscoveryProbeConfig v1 = null;
    for (DiscoveryProbeConfig probe : effectiveProbes()) {
      String method = normalizeMethod(probe.method());
      if ("SNMP_V3".equals(method)) {
        v3 = probe;
      } else if ("SNMP_V2".equals(method)) {
        v2 = probe;
      } else if ("SNMP_V1".equals(method)) {
        v1 = probe;
      }
    }
    if (v3 != null) {
      return v3;
    }
    if (v2 != null) {
      return v2;
    }
    return v1;
  }

  public static String snmpVersionForProbe(DiscoveryProbeConfig probe) {
    if (probe == null) {
      return "v2c";
    }
    return switch (normalizeMethod(probe.method())) {
      case "SNMP_V1" -> "v1";
      case "SNMP_V3" -> "v3";
      default -> "v2c";
    };
  }

  public static int defaultPortForMethod(String method) {
    return switch (normalizeMethod(method)) {
      case "FTP" -> 21;
      case "HTTP" -> 80;
      case "HTTPS" -> 443;
      case "IMAP" -> 143;
      case "LDAP" -> 389;
      case "NNTP" -> 119;
      case "POP" -> 110;
      case "SMTP" -> 25;
      case "SNMP_V1", "SNMP_V2", "SNMP_V3" -> 161;
      case "SSH" -> 22;
      case "TELNET" -> 23;
      case "TCP" -> 80;
      default -> 1;
    };
  }

  public static int resolveProbePort(DiscoveryProbeConfig probe) {
    if (probe.port() != null && probe.port() > 0) {
      return probe.port();
    }
    return defaultPortForMethod(probe.method());
  }

  private static String normalizeMethod(String method) {
    return method == null ? "" : method.trim().toUpperCase();
  }
}
