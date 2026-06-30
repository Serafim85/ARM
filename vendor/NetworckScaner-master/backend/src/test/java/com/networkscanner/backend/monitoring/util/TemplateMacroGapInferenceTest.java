package com.networkscanner.backend.monitoring.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTemplateRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateMacroGapInferenceTest {

  @Test
  void infersGenericAndVfsDonorsForMissingInterfaceAndFilesystemMacros() {
    ZabbixTemplateRecord template = new ZabbixTemplateRecord(
        "uuid-1",
        "Gap Test",
        "Gap Test",
        null,
        null,
        List.of(),
        List.of(new ZabbixDiscoveryRuleRecord(
            "rule-1",
            "IF discovery",
            "SNMP_AGENT",
            null,
            "if.discovery",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(new ZabbixTriggerRecord(
                "tr-1",
                "avg(/Gap Test/net.if.in[{#SNMPINDEX}])>{$IF.UTIL.MAX:\"{#IFNAME}\"}",
                null,
                null,
                null,
                null,
                null,
                "High util",
                "WARNING",
                null
            )),
            List.of()
        )),
        List.of(),
        null,
        null,
        null
    );

    List<String> inferred = TemplateMacroGapInference.inferDonorIds(template, Map.of());
    assertTrue(inferred.contains("generic-snmp-macros"));
  }
}
