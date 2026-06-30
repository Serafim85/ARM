package com.networkscanner.backend.network.scan.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryConditionRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryFilterRecord;
import com.networkscanner.backend.monitoring.impl.LinuxBySnmpFixtureSupport;
import com.networkscanner.backend.monitoring.util.ZabbixTemplateMacroSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinuxBySnmpDiscoveryFilterTest {

  @Test
  void discoveryFilterUsesResolvedTemplateMacros() {
    var template = LinuxBySnmpFixtureSupport.template();
    Map<String, String> macros = Map.of(
        "{#IFNAME}", "ens18",
        "{#IFOPERSTATUS}", "1",
        "{#IFADMINSTATUS}", "1",
        "{#IFALIAS}", "",
        "{#IFDESCR}", "ens18",
        "{#IFTYPE}", "6"
    );
    ZabbixDiscoveryFilterRecord filter = template.discoveryRule("net.if.discovery").filter();
    assertTrue(SnmpScanServiceImpl.matchesDiscoveryFilter(filter, macros));

    Map<String, String> templateMacros = new LinkedHashMap<>(template.templateMacros());
    templateMacros.put("{$NET.IF.IFNAME.NOT_MATCHES}", "^ens18$");
    String resolvedNotMatches = ZabbixTemplateMacroSupport.applyTemplateMacros(
        "{$NET.IF.IFNAME.NOT_MATCHES}",
        templateMacros
    );
    ZabbixDiscoveryFilterRecord strictFilter = new ZabbixDiscoveryFilterRecord(
        filter.evaltype(),
        List.of(new ZabbixDiscoveryConditionRecord("{#IFNAME}", resolvedNotMatches, "NOT_MATCHES_REGEX"))
    );
    assertFalse(SnmpScanServiceImpl.matchesDiscoveryFilter(strictFilter, macros));
  }
}
