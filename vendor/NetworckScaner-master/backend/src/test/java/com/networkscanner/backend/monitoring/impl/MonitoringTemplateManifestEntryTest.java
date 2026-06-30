package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifestEntry;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MonitoringTemplateManifestEntryTest {

  @Test
  void overlayUploadedMetadataPreservesMacroDonorsAndZabbixTemplate() throws Exception {
    MonitoringTemplateManifestEntry entry = new MonitoringTemplateManifestEntry(
        "uploaded-id",
        "uploaded.yaml",
        "1.0.0",
        "SNMP",
        null,
        "Cisco",
        ".*",
        10,
        "parent-template",
        java.util.List.of("vfs-fs-macros"),
        "Uploaded Zabbix Template",
        false
    );
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setVendor("Juniper");
    uploaded.setModelRegex("SRX.*");

    MonitoringTemplateResolverImpl resolver = new MonitoringTemplateResolverImpl(
        new com.fasterxml.jackson.databind.ObjectMapper(),
        null,
        null,
        null,
        null,
        new MonitoringTemplateObfuscator()
    );
    Method overlay = MonitoringTemplateResolverImpl.class.getDeclaredMethod(
        "overlayUploadedMetadata",
        MonitoringTemplateManifestEntry.class,
        UploadedMonitoringTemplateEntity.class
    );
    overlay.setAccessible(true);
    MonitoringTemplateManifestEntry result = (MonitoringTemplateManifestEntry) overlay.invoke(
        resolver,
        entry,
        uploaded
    );

    assertEquals("Juniper", result.vendor());
    assertEquals("SRX.*", result.modelRegex());
    assertEquals(java.util.List.of("vfs-fs-macros"), result.macroDonors());
    assertEquals("Uploaded Zabbix Template", result.zabbixTemplate());
    assertEquals(false, result.uiVisible());
  }

  @Test
  void overlayUploadedMetadataUsesPriorityFromEntity() throws Exception {
    MonitoringTemplateManifestEntry entry = new MonitoringTemplateManifestEntry(
        "uploaded-id",
        "uploaded.yaml",
        "1.0.0",
        "SNMP",
        null,
        "Cisco",
        ".*",
        10,
        null,
        null,
        null,
        true
    );
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setPriority(42);

    MonitoringTemplateResolverImpl resolver = new MonitoringTemplateResolverImpl(
        new com.fasterxml.jackson.databind.ObjectMapper(),
        null,
        null,
        null,
        null,
        new MonitoringTemplateObfuscator()
    );
    Method overlay = MonitoringTemplateResolverImpl.class.getDeclaredMethod(
        "overlayUploadedMetadata",
        MonitoringTemplateManifestEntry.class,
        UploadedMonitoringTemplateEntity.class
    );
    overlay.setAccessible(true);
    MonitoringTemplateManifestEntry result = (MonitoringTemplateManifestEntry) overlay.invoke(
        resolver,
        entry,
        uploaded
    );

    assertEquals(42, result.priority());
  }

  @Test
  void buildFilesystemEntryLeavesMacroDonorsNull() throws Exception {
    String yaml = """
        zabbix_export:
          version: "8.0"
          templates:
            - template: "Filesystem Vendor"
              name: "Filesystem Vendor"
              macros: []
        """;
    MonitoringTemplateResolverImpl resolver = new MonitoringTemplateResolverImpl(
        new com.fasterxml.jackson.databind.ObjectMapper(),
        null,
        null,
        null,
        null,
        new MonitoringTemplateObfuscator()
    );
    Method buildEntry = MonitoringTemplateResolverImpl.class.getDeclaredMethod(
        "buildFilesystemEntry",
        java.nio.file.Path.class,
        String.class
    );
    buildEntry.setAccessible(true);
    MonitoringTemplateManifestEntry entry = (MonitoringTemplateManifestEntry) buildEntry.invoke(
        resolver,
        java.nio.file.Path.of("vendors/vendor_by_snmp.template"),
        yaml
    );
    assertNull(entry.macroDonors());
    assertEquals("Filesystem Vendor", entry.zabbixTemplate());
  }
}
