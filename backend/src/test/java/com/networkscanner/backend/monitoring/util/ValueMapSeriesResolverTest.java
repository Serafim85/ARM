package com.networkscanner.backend.monitoring.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.networkscanner.backend.monitoring.dto.MonitoringTemplateCoverageReportDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ValueMapSeriesMeta;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixValueMapRuntime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueMapSeriesResolverTest {

  private static final Map<String, String> IF_OPER_STATUS = Map.of(
      "1", "up",
      "2", "down",
      "4", "unknown"
  );

  private static final Map<String, String> HOST_AVAILABLE = Map.of(
      "0", "not available",
      "1", "available",
      "2", "unknown"
  );

  @Test
  void resolvesStaticItemValueMap() {
    ResolvedMonitoringTemplate template = templateWithItems(
        Map.of(
            "zabbix[host,snmp,available]",
            item("zabbix[host,snmp,available]", "zabbix.host.available")
        ),
        Map.of(
            "zabbix.host.available",
            new ZabbixValueMapRuntime("vm-1", "zabbix.host.available", HOST_AVAILABLE)
        )
    );

    ValueMapSeriesMeta meta = ValueMapSeriesResolver.resolve(template, "zabbix[host,snmp,available]");

    assertNotNull(meta);
    assertEquals("zabbix.host.available", meta.valueMapName());
    assertEquals("available", meta.mappings().get("1"));
  }

  @Test
  void resolvesDiscoveryPrototypeValueMap() {
    ZabbixItemRuntime prototype = item("net.if.status[{#IFNAME}]", "IF-MIB::ifOperStatus");
    ZabbixDiscoveryRuleRuntime rule = new ZabbixDiscoveryRuleRuntime(
        "dr-1",
        "net.if.discovery",
        "Network interfaces",
        "SNMP_AGENT",
        "1.3.6.1.2.1.2.2.1",
        null,
        List.of(),
        List.of(),
        60,
        86400,
        null,
        List.of(prototype),
        List.of(),
        List.of()
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "snmp",
        "Template",
        "",
        null,
        "Cisco",
        ".*",
        1,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 1000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of("net.if.discovery", rule),
        Map.of("IF-MIB::ifOperStatus", new ZabbixValueMapRuntime("vm-2", "IF-MIB::ifOperStatus", IF_OPER_STATUS)),
        Map.of(),
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );

    ValueMapSeriesMeta meta = ValueMapSeriesResolver.resolve(template, "net.if.status[Gi0/1]");

    assertNotNull(meta);
    assertEquals("IF-MIB::ifOperStatus", meta.valueMapName());
    assertEquals("up", meta.mappings().get("1"));
    assertEquals("down", meta.mappings().get("2"));
  }

  @Test
  void returnsNullWhenNoValueMap() {
    ResolvedMonitoringTemplate template = templateWithItems(
        Map.of("system.cpu.util", item("system.cpu.util", null)),
        Map.of()
    );

    assertNull(ValueMapSeriesResolver.resolve(template, "system.cpu.util"));
  }

  @Test
  void resolveAllSkipsMetricsWithoutValueMap() {
    ResolvedMonitoringTemplate template = templateWithItems(
        Map.of(
            "zabbix[host,snmp,available]",
            item("zabbix[host,snmp,available]", "zabbix.host.available"),
            "system.cpu.util",
            item("system.cpu.util", null)
        ),
        Map.of(
            "zabbix.host.available",
            new ZabbixValueMapRuntime("vm-1", "zabbix.host.available", HOST_AVAILABLE)
        )
    );

    Map<String, ValueMapSeriesMeta> all = ValueMapSeriesResolver.resolveAll(
        template,
        List.of("zabbix[host,snmp,available]", "system.cpu.util")
    );

    assertEquals(1, all.size());
    assertNotNull(all.get("zabbix[host,snmp,available]"));
  }

  private static ResolvedMonitoringTemplate templateWithItems(
      Map<String, ZabbixItemRuntime> items,
      Map<String, ZabbixValueMapRuntime> valueMaps
  ) {
    return new ResolvedMonitoringTemplate(
        "tpl",
        "snmp",
        "Template",
        "",
        null,
        "Cisco",
        ".*",
        1,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 1000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        new LinkedHashMap<>(items),
        Map.of(),
        new LinkedHashMap<>(valueMaps),
        Map.of(),
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );
  }

  private static ZabbixItemRuntime item(String key, String valueMapName) {
    return new ZabbixItemRuntime(
        "uuid-" + key,
        key,
        key,
        "SNMP_AGENT",
        null,
        60,
        "UNSIGNED",
        "",
        null,
        null,
        null,
        null,
        List.of(),
        valueMapName,
        false,
        null
    );
  }
}
