package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateSnmp(
    String version,
    String communityDefault,
    Integer timeoutMs,
    Integer retries,
    Integer port,
    String securityUsername,
    String authProtocol,
    String authPassword,
    String privacyProtocol,
    String privacyPassword
) {

  public boolean isV3() {
    return version != null && version.equalsIgnoreCase("v3");
  }

  public static MonitoringTemplateSnmp v2c(String community, Integer timeoutMs, Integer retries, Integer port) {
    return new MonitoringTemplateSnmp(
        "v2c",
        community,
        timeoutMs,
        retries,
        port,
        null,
        null,
        null,
        null,
        null
    );
  }
}
