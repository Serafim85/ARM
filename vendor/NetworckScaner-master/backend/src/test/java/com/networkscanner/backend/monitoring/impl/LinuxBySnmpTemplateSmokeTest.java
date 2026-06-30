package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.util.LinuxBySnmpWalkSpecs;
import org.junit.jupiter.api.Test;

class LinuxBySnmpTemplateSmokeTest {

  @Test
  void resolvesLinuxBySnmpTemplateWithMasterWalksAndDependentDiscovery() {
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();

    assertEquals(LinuxBySnmpFixtureSupport.templateId(), template.id());
    assertTrue(template.items().containsKey(LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY));
    assertTrue(template.items().containsKey(LinuxBySnmpWalkSpecs.CPU_LOAD_WALK_KEY));
    assertTrue(template.items().containsKey(LinuxBySnmpWalkSpecs.CPU_WALK_KEY));
    assertTrue(template.items().containsKey(LinuxBySnmpWalkSpecs.VFS_FS_WALK_KEY));

    assertDependentDiscovery(template, "net.if.discovery", LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY);
    assertDependentDiscovery(template, "vfs.fs.discovery[snmp]", LinuxBySnmpWalkSpecs.VFS_FS_WALK_KEY);
    assertDependentDiscovery(template, "cpu.discovery[snmp]", "system.cpu.num[snmp]");

    assertNotNull(template.templateMacros());
    assertFalse(template.templateMacros().isEmpty());
    assertTrue(template.templateMacros().containsKey("{$NET.IF.IFNAME.MATCHES}")
        || template.templateMacros().keySet().stream().anyMatch(k -> k.contains("NET.IF.IFNAME.MATCHES")));
    assertTrue(template.templateMacros().keySet().stream()
        .anyMatch(k -> k.contains("VFS.FS.PUSED.MAX.CRIT")));
  }

  private static void assertDependentDiscovery(
      ResolvedMonitoringTemplate template,
      String ruleKey,
      String masterKey
  ) {
    ZabbixDiscoveryRuleRuntime rule = template.discoveryRule(ruleKey);
    assertNotNull(rule, "missing discovery rule: " + ruleKey);
    assertTrue(rule.isDependent());
    assertEquals(masterKey, rule.masterItemKey());
  }
}
