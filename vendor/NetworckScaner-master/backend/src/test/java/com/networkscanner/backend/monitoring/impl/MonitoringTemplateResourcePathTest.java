package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MonitoringTemplateResourcePathTest {

  @Test
  void resolvesYamlManifestPathsToTemplateFiles() {
    assertEquals(
        "vendors/mikrotik_by_snmp.template",
        MonitoringTemplateResolverImpl.resolveTemplateResourcePath("vendors/mikrotik_by_snmp.yaml")
    );
    assertEquals(
        "manifest.template",
        MonitoringTemplateResolverImpl.resolveTemplateResourcePath("manifest.template")
    );
  }
}
