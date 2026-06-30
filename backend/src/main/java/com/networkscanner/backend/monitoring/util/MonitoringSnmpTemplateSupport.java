package com.networkscanner.backend.monitoring.util;

import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.util.Map;

/**
 * Слияние SNMP-параметров шаблона с переопределениями устройства (порт, community, SNMP v3).
 */
public final class MonitoringSnmpTemplateSupport {

  private MonitoringSnmpTemplateSupport() {
  }

  /**
   * Копия шаблона с укороченными SNMP timeout/retry для быстрого probe (pre-SNMP gate).
   */
  public static ResolvedMonitoringTemplate withSnmpProbeTimeouts(
      ResolvedMonitoringTemplate template,
      int timeoutMs,
      int retries
  ) {
    if (template == null) {
      return null;
    }
    MonitoringTemplateSnmp snmp = template.snmp();
    MonitoringTemplateSnmp probeSnmp = new MonitoringTemplateSnmp(
        snmp == null ? "v2c" : snmp.version(),
        snmp == null ? "public" : snmp.communityDefault(),
        Math.max(timeoutMs, 1),
        Math.max(retries, 0),
        snmp == null ? 161 : snmp.port(),
        snmp == null ? null : snmp.securityUsername(),
        snmp == null ? null : snmp.authProtocol(),
        snmp == null ? null : snmp.authPassword(),
        snmp == null ? null : snmp.privacyProtocol(),
        snmp == null ? null : snmp.privacyPassword()
    );
    return copyWithSnmp(template, probeSnmp);
  }

  public static ResolvedMonitoringTemplate applyDeviceSnmpOverrides(
      ResolvedMonitoringTemplate template,
      MonitoredDeviceEntity device
  ) {
    if (template == null || device == null) {
      return template;
    }
    MonitoringTemplateSnmp merged = mergeDeviceSnmp(template.snmp(), device);
    if (merged == template.snmp()) {
      return template;
    }
    return copyWithSnmp(template, merged);
  }

  public static void applyActivationSnmp(
      MonitoredDeviceEntity entity,
      DeviceScanResult device,
      MonitoringSnmpCredentials credentials
  ) {
    if (entity == null || credentials == null) {
      return;
    }
    String version = firstNonBlank(credentials.snmpVersion(), resolveVersionFromPollingStatus(device.pollingStatus()));
    entity.setSnmpVersion(version);
    if (isV3(version)) {
      entity.setSnmpCommunity(null);
      entity.setSnmpSecurityUsername(trimToNull(credentials.securityUsername()));
      entity.setSnmpAuthProtocol(firstNonBlank(credentials.authProtocol(), "SHA"));
      entity.setSnmpAuthPassword(trimToNull(credentials.authPassword()));
      entity.setSnmpPrivacyProtocol(firstNonBlank(credentials.privacyProtocol(), "AES"));
      entity.setSnmpPrivacyPassword(trimToNull(credentials.privacyPassword()));
    } else {
      entity.setSnmpCommunity(firstNonBlank(credentials.community(), entity.getSnmpCommunity()));
      entity.setSnmpSecurityUsername(null);
      entity.setSnmpAuthProtocol(null);
      entity.setSnmpAuthPassword(null);
      entity.setSnmpPrivacyProtocol(null);
      entity.setSnmpPrivacyPassword(null);
    }
  }

  public static String resolveVersionFromPollingStatus(String pollingStatus) {
    if (pollingStatus == null || pollingStatus.isBlank()) {
      return "v2c";
    }
    String normalized = pollingStatus.trim().toLowerCase();
    if (normalized.contains("v3")) {
      return "v3";
    }
    if (normalized.contains("v1")) {
      return "v1";
    }
    return "v2c";
  }

  private static MonitoringTemplateSnmp mergeDeviceSnmp(MonitoringTemplateSnmp base, MonitoredDeviceEntity device) {
    boolean hasPort = device.getSnmpPort() != null && device.getSnmpPort() > 0;
    boolean hasVersion = device.getSnmpVersion() != null && !device.getSnmpVersion().isBlank();
    boolean hasCommunity = device.getSnmpCommunity() != null && !device.getSnmpCommunity().isBlank();
    boolean hasV3User = device.getSnmpSecurityUsername() != null && !device.getSnmpSecurityUsername().isBlank();
    if (!hasPort && !hasVersion && !hasCommunity && !hasV3User) {
      return base;
    }
    String version = hasVersion ? device.getSnmpVersion().trim() : (base == null ? "v2c" : base.version());
    return new MonitoringTemplateSnmp(
        version,
        hasCommunity ? device.getSnmpCommunity() : (base == null ? "public" : base.communityDefault()),
        base == null ? 3000 : base.timeoutMs(),
        base == null ? 1 : base.retries(),
        hasPort ? device.getSnmpPort() : (base == null ? 161 : base.port()),
        hasV3User ? device.getSnmpSecurityUsername() : (base == null ? null : base.securityUsername()),
        device.getSnmpAuthProtocol() != null ? device.getSnmpAuthProtocol()
            : (base == null ? null : base.authProtocol()),
        device.getSnmpAuthPassword() != null ? device.getSnmpAuthPassword()
            : (base == null ? null : base.authPassword()),
        device.getSnmpPrivacyProtocol() != null ? device.getSnmpPrivacyProtocol()
            : (base == null ? null : base.privacyProtocol()),
        device.getSnmpPrivacyPassword() != null ? device.getSnmpPrivacyPassword()
            : (base == null ? null : base.privacyPassword())
    );
  }

  private static ResolvedMonitoringTemplate copyWithSnmp(
      ResolvedMonitoringTemplate template,
      MonitoringTemplateSnmp snmp
  ) {
    return new ResolvedMonitoringTemplate(
        template.id(),
        template.type(),
        template.name(),
        template.description(),
        template.extendsTemplate(),
        template.vendor(),
        template.modelRegex(),
        template.priority(),
        template.schemaVersion(),
        template.packVersion(),
        template.templateVersion(),
        snmp,
        template.oids(),
        template.units(),
        template.preprocessingFunctions(),
        template.metrics(),
        template.itemTemplateIds(),
        template.items(),
        template.discoveryRules(),
        template.valueMaps(),
        template.triggers(),
        template.graphs(),
        template.templateMacros() == null ? Map.of() : template.templateMacros(),
        template.coverage(),
        template.uiVisible()
    );
  }

  private static boolean isV3(String version) {
    return version != null && version.equalsIgnoreCase("v3");
  }

  private static String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
