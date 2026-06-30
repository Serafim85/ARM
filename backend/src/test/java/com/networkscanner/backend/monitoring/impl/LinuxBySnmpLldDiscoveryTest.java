package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinuxBySnmpLldDiscoveryTest {

  @BeforeEach
  void resetFixtureCaches() {
    LinuxBySnmpFixtureSupport.resetCaches();
  }

  @Test
  void discoversFilesystemsFromVfsWalk() {
    SnmpScanService snmp = LinuxBySnmpFixtureSupport.snmpServiceFromFixtures();
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();

    List<DiscoveryInstanceRuntime> instances = snmp.executeDiscovery(
        LinuxBySnmpFixtureSupport.DEVICE_IP,
        template,
        template.discoveryRule("vfs.fs.discovery[snmp]"),
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );

    assertFalse(instances.isEmpty());
    boolean hasRoot = instances.stream()
        .anyMatch(i -> "/".equals(i.macros().get("{#FSNAME}")) || "/".equals(i.macros().get("{#FSDEVICE}")));
    assertTrue(hasRoot || instances.stream().anyMatch(i -> i.macros().get("{#FSNAME}") != null));
    instances.forEach(i -> assertNotNull(i.macros().get("{#SNMPINDEX}")));
  }

  @Test
  void discoversCpusFromCpuWalk() {
    SnmpScanService snmp = LinuxBySnmpFixtureSupport.snmpServiceFromFixtures();
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();

    List<DiscoveryInstanceRuntime> instances = snmp.executeDiscovery(
        LinuxBySnmpFixtureSupport.DEVICE_IP,
        template,
        template.discoveryRule("cpu.discovery[snmp]"),
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );

    assertFalse(instances.isEmpty());
    assertTrue(instances.stream().allMatch(i -> i.macros().containsKey("{#SNMPINDEX}")));
  }
}
