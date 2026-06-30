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

class LinuxBySnmpNetIfDiscoveryTest {

  @BeforeEach
  void resetFixtureCaches() {
    LinuxBySnmpFixtureSupport.resetCaches();
  }

  @Test
  void discoversEns18FromWalkFixture() {
    SnmpScanService snmp = LinuxBySnmpFixtureSupport.snmpServiceFromFixtures();
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();

    List<DiscoveryInstanceRuntime> instances = snmp.executeDiscovery(
        LinuxBySnmpFixtureSupport.DEVICE_IP,
        template,
        template.discoveryRule("net.if.discovery"),
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );

    assertFalse(instances.isEmpty());
    assertTrue(instances.size() >= 1 && instances.size() <= 20, () -> "discovered=" + instances.size());

    DiscoveryInstanceRuntime ens18 = instances.stream()
        .filter(i -> "ens18".equals(i.macros().get("{#IFNAME}")))
        .findFirst()
        .orElse(null);
    assertNotNull(ens18, "ens18 must be discovered");
    assertEqualsMacro("2", ens18.macros().get("{#SNMPINDEX}"));
    assertNotNull(ens18.macros().get("{#IFOPERSTATUS}"));
    assertNotNull(ens18.macros().get("{#IFADMINSTATUS}"));
  }

  private static void assertEqualsMacro(String expected, String actual) {
    assertNotNull(actual);
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }
}
