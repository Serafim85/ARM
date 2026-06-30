package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinuxBySnmpDependentDiscoveryRunnerTest {

  @BeforeEach
  void resetFixtureCaches() {
    LinuxBySnmpFixtureSupport.resetCaches();
  }

  @Test
  void netIfDiscoveryFromFixtureWalk() {
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();
    var instances = LinuxBySnmpDependentDiscoveryRunner.execute(
        template,
        template.discoveryRule("net.if.discovery"),
        LinuxBySnmpFixtureSupport.getFixtures(),
        LinuxBySnmpFixtureSupport.walkFixtures(),
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );
    assertFalse(instances.isEmpty(), () -> "instances empty, check walk JSON and filter");
    assertTrue(
        instances.stream().anyMatch(i -> "ens18".equals(i.macros().get("{#IFNAME}"))),
        () -> "macros sample: " + instances.stream().limit(3).map(i -> i.macros()).toList()
    );
  }
}
