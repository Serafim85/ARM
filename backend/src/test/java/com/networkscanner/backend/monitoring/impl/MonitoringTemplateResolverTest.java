package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSource;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitoringTemplateResolverTest {

  private MonitoringTemplateResolver resolver;
  private UploadedMonitoringTemplateRepository uploadedRepository;
  private AppUserRepository appUserRepository;
  private MonitoringTemplateObfuscator obfuscator;

  @BeforeEach
  void setUp() {
    uploadedRepository = mock(UploadedMonitoringTemplateRepository.class);
    appUserRepository = mock(AppUserRepository.class);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of());
    obfuscator = new MonitoringTemplateObfuscator();
    MonitoringTemplatePriorityOverrideRepository priorityOverrideRepository =
        mock(MonitoringTemplatePriorityOverrideRepository.class);
    when(priorityOverrideRepository.findAll()).thenReturn(List.of());
    resolver = new MonitoringTemplateResolverImpl(
        new ObjectMapper(),
        uploadedRepository,
        priorityOverrideRepository,
        appUserRepository,
        new MonitoringTemplateArchiveReader(obfuscator),
        obfuscator
    );
    resolver.initialize();
  }

  @Test
  void resolvesDefaultTemplateForUnknownDevice() {
    ResolvedMonitoringTemplate template = resolver.resolveForDevice((String) null, "UnknownVendor", "ModelX");
    assertEquals("network-generic-device-by-snmp", template.id());
  }

  @Test
  void resolvesVendorSpecificTemplateForCisco() {
    ResolvedMonitoringTemplate template = resolver.resolveForDevice((String) null, "Cisco", "C2600");
    assertEquals("cisco-ios-by-snmp", template.id());
  }

  @Test
  void resolvesModelSpecificZabbixTemplateForCiscoSg500x() {
    ResolvedMonitoringTemplate template = resolver.resolveForDevice((String) null, "Cisco", "SG500X-48P");
    assertEquals("cisco-ios-by-snmp", template.id());
  }

  @Test
  void listTemplatesShowsSingleCiscoEntry() {
    List<MonitoringTemplateSummaryDto> templates = resolver.listTemplates();
    long ciscoCount = templates.stream()
        .filter(item -> "Cisco".equalsIgnoreCase(item.vendor()))
        .count();
    assertEquals(1, ciscoCount);
    assertTrue(templates.stream().anyMatch(item -> "cisco-ios-by-snmp".equals(item.id())));
    assertFalse(templates.stream().anyMatch(item -> "cisco-ios-prior-to-12-0-3-t-by-snmp".equals(item.id())));
    assertFalse(templates.stream().anyMatch(item -> "cisco-ios-12-0-3-t-12-2-3-5-by-snmp".equals(item.id())));
  }

  @Test
  void aggregatedCiscoTemplateMergesAllThreeLayers() {
    ResolvedMonitoringTemplate aggregated = resolver.resolveTemplateById("cisco-ios-by-snmp");
    ResolvedMonitoringTemplate prior = resolver.resolveTemplateById("cisco-ios-prior-to-12-0-3-t-by-snmp");
    ResolvedMonitoringTemplate mid = resolver.resolveTemplateById("cisco-ios-12-0-3-t-12-2-3-5-by-snmp");

    assertTrue(aggregated.items().size() >= prior.items().size());
    assertTrue(aggregated.items().size() >= mid.items().size());
    assertTrue(aggregated.items().containsKey("system.cpu.util[avgBusy5]"));
    assertTrue(aggregated.discoveryRules().containsKey("cpu.discovery"));
    assertTrue(aggregated.items().containsKey("icmpping"));
    assertFalse(aggregated.triggers().isEmpty());

    ZabbixDiscoveryRuleRuntime cpuDiscovery = aggregated.discoveryRules().get("cpu.discovery");
    assertNotNull(cpuDiscovery);
    assertFalse(cpuDiscovery.itemPrototypes().isEmpty());

    MonitoringTemplateDetailsDto details = resolver.describeTemplate("cisco-ios-by-snmp");
    assertTrue(details.items().size() >= aggregated.items().size());
    assertTrue(details.items().stream().anyMatch(item -> item.discoveryPrototype()));

    ZabbixDiscoveryRuleRuntime priorCpuDiscovery =
        resolver.resolveTemplateById("cisco-ios-prior-to-12-0-3-t-by-snmp").discoveryRules().get("cpu.discovery");
    if (priorCpuDiscovery != null && !priorCpuDiscovery.itemPrototypes().isEmpty()) {
      assertTrue(
          cpuDiscovery.itemPrototypes().size() >= priorCpuDiscovery.itemPrototypes().size(),
          () -> "Merged cpu.discovery should keep LLD prototypes from parent layers"
      );
    }
  }

  @Test
  void manualSelectionOverridesAutoDetectionAndInheritsParentOids() {
    ResolvedMonitoringTemplate template = resolver.resolveForDevice("cisco-ios-by-snmp", "UnknownVendor", "ModelY");
    assertEquals("cisco-ios-by-snmp", template.id());
    assertEquals("1.3.6.1.2.1.1.1.0", template.oids().details().get("sysDescr"));
  }

  @Test
  void mergesMultipleTemplatesWithFirstWinsByItemKey() {
    ResolvedMonitoringTemplate merged = resolver.resolveMergedTemplates(List.of(
        "network-generic-device-by-snmp",
        "cisco-ios-by-snmp"
    ));
    assertEquals("network-generic-device-by-snmp", merged.id());
    assertTrue(merged.items().containsKey("icmpping"));
    // both templates define icmpping, but the first template must win on conflicts
    assertEquals("network-generic-device-by-snmp", merged.itemTemplateIds().get("icmpping"));
  }

  @Test
  void compiledZabbixTemplateContainsDiscoveryRulesAndTriggers() {
    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("cisco-ios-by-snmp");
    assertEquals("2026.05.08-vendors", template.packVersion());
    assertEquals("8.0", template.templateVersion());
    assertTrue(template.discoveryRules().containsKey("cpu.discovery"));
    assertTrue(template.items().containsKey("icmpping"));
    assertFalse(template.triggers().isEmpty());
  }

  @Test
  void compilesDiscoveryFilterWithResolvedTemplateMacros() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-filter-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.04.02"
        defaultTemplateId: uploaded-filter-template
        templates:
          - id: uploaded-filter-template
            file: uploaded-filter-template.yaml
            version: "1.0.1"
            type: SNMP
            zabbixTemplate: Uploaded Filter Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "6.0",
            "templates": [
              {
                "template": "Uploaded Filter Template",
                "name": "Uploaded Filter Template",
                "macros": [
                  {
                    "macro": "{$NET.IF.IFNAME.MATCHES}",
                    "value": "^Gi.*$"
                  }
                ],
                "discovery_rules": [
                  {
                    "uuid": "rule-1",
                    "name": "Interfaces",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "discovery[{#IFNAME},1.3.6.1.2.1.31.1.1.1.1]",
                    "key": "if.discovery",
                    "filter": {
                      "evaltype": "AND",
                      "conditions": [
                        {
                          "macro": "{#IFNAME}",
                          "value": "{$NET.IF.IFNAME.MATCHES}",
                          "operator": "MATCHES_REGEX"
                        }
                      ]
                    },
                    "item_prototypes": [
                      {
                        "uuid": "item-1",
                        "name": "ifName",
                        "type": "SNMP_AGENT",
                        "snmp_oid": "1.3.6.1.2.1.31.1.1.1.1.{#SNMPINDEX}",
                        "key": "ifName[{#IFNAME}]",
                        "delay": "60"
                      }
                    ]
                  }
                ],
                "items": [
                  {
                    "uuid": "item-root",
                    "name": "sysName",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "1.3.6.1.2.1.1.5.0",
                    "key": "sysName",
                    "delay": "60"
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));

    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-filter-template");
    assertEquals(
        "^Gi.*$",
        template.discoveryRule("if.discovery").filter().conditions().get(0).value()
    );
  }

  @Test
  void keepsIcmpSimpleItemsWithoutSnmpOid() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-icmp-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.04.02"
        defaultTemplateId: uploaded-icmp-template
        templates:
          - id: uploaded-icmp-template
            file: uploaded-icmp-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Uploaded ICMP Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Uploaded ICMP Template",
                "name": "Uploaded ICMP Template",
                "items": [
                  {
                    "uuid": "icmp-item-1",
                    "name": "ICMP ping",
                    "type": "SIMPLE",
                    "key": "icmpping",
                    "delay": "30"
                  },
                  {
                    "uuid": "icmp-item-2",
                    "name": "ICMP loss",
                    "type": "SIMPLE",
                    "key": "icmppingloss",
                    "delay": "30"
                  },
                  {
                    "uuid": "icmp-item-3",
                    "name": "ICMP response",
                    "type": "SIMPLE",
                    "key": "icmppingsec",
                    "delay": "30"
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));

    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-icmp-template");
    assertTrue(template.items().containsKey("icmpping"));
    assertTrue(template.items().containsKey("icmppingloss"));
    assertTrue(template.items().containsKey("icmppingsec"));
    assertEquals("SIMPLE", template.items().get("icmpping").type());
    assertTrue(template.items().get("icmpping").snmpOid() == null);
  }

  @Test
  void resolvesTemplateMacrosInsideTriggerExpressions() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-icmp-trigger-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.04.02"
        defaultTemplateId: uploaded-icmp-trigger-template
        templates:
          - id: uploaded-icmp-trigger-template
            file: uploaded-icmp-trigger-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Uploaded ICMP Trigger Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Uploaded ICMP Trigger Template",
                "name": "Uploaded ICMP Trigger Template",
                "macros": [
                  {
                    "macro": "{$ICMP_RESPONSE_TIME_WARN}",
                    "value": "0.15"
                  }
                ],
                "items": [
                  {
                    "uuid": "icmp-item-1",
                    "name": "ICMP response",
                    "type": "SIMPLE",
                    "key": "icmppingsec",
                    "delay": "30",
                    "triggers": [
                      {
                        "uuid": "trigger-1",
                        "name": "High response time",
                        "expression": "avg(/Uploaded ICMP Trigger Template/icmppingsec,5m)>{$ICMP_RESPONSE_TIME_WARN}",
                        "priority": "WARNING"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));

    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-icmp-trigger-template");
    String expression = template.triggers().values().iterator().next().expression();
    assertTrue(expression.contains(">0.15"));
    assertFalse(expression.contains("{$ICMP_RESPONSE_TIME_WARN}"));

    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        expression,
        java.time.OffsetDateTime.parse("2026-04-05T20:00:00Z"),
        (metricName, window, timestamp) -> List.of(0.0007211333333333334d)
    );
    assertFalse(evaluation.breached());
  }

  @Test
  void skipsTriggerWhenTemplateMacroRemainsUnresolved() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-broken-macro-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.05.21"
        defaultTemplateId: uploaded-broken-macro-template
        templates:
          - id: uploaded-broken-macro-template
            file: uploaded-broken-macro-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Uploaded Broken Macro Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Uploaded Broken Macro Template",
                "name": "Uploaded Broken Macro Template",
                "items": [
                  {
                    "uuid": "temp-item-1",
                    "name": "Temperature",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "1.3.6.1.4.1.9.9.13.1.3.1.3.1",
                    "key": "temp.sensor[1]",
                    "delay": "60",
                    "triggers": [
                      {
                        "uuid": "temp-trigger-1",
                        "name": "Temperature high",
                        "expression": "last(/Uploaded Broken Macro Template/temp.sensor[1])>{$TEMP_WARN}",
                        "priority": "WARNING"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));

    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-broken-macro-template");
    assertTrue(template.triggers().isEmpty());
  }

  @Test
  void listTemplatesReturnsVersionMetadata() {
    List<MonitoringTemplateSummaryDto> templates = resolver.listTemplates();
    MonitoringTemplateSummaryDto summary = templates.stream()
        .filter(item -> "cisco-ios-by-snmp".equals(item.id()))
        .findFirst()
        .orElseThrow();
    assertEquals("1", summary.schemaVersion());
    assertEquals("2026.05.08-vendors", summary.packVersion());
    assertEquals("8.0", summary.templateVersion());
    assertEquals(MonitoringTemplateSource.SYSTEM, summary.source());
    assertFalse(summary.deletable());
  }

  @Test
  void listTemplatesMarksUploadedDefinitions() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.04.02"
        defaultTemplateId: uploaded-template
        templates:
          - id: uploaded-template
            file: uploaded-template.yaml
            version: "1.0.1"
            type: SNMP
            extends: network-generic-device-by-snmp
            zabbixTemplate: Uploaded Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "6.0",
            "templates": [
              {
                "template": "Uploaded Template",
                "name": "Uploaded Template",
                "description": "Uploaded package",
                "items": [
                  {
                    "uuid": "item-1",
                    "name": "Device name",
                    "type": "SNMP_AGENT",
                    "snmp_oid": ".1.3.6.1.2.1.1.5.0",
                    "key": "sysName",
                    "delay": "60"
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));

    resolver.initialize();

    MonitoringTemplateSummaryDto summary = resolver.listTemplates().stream()
        .filter(item -> "uploaded-template".equals(item.id()))
        .findFirst()
        .orElseThrow();
    assertEquals(MonitoringTemplateSource.UPLOADED, summary.source());
    assertTrue(summary.deletable());
  }

  @Test
  void previewsDirectCiscoTemplateFile() {
    MonitoringTemplateImportPreviewDto preview = resolver.previewArchive(
        "cisco-ios-snmp.template",
        encodeTemplate("""
            zabbix_export:
              version: "7.4"
              templates:
                - template: "Cisco IOS SNMP"
                  name: "Cisco IOS SNMP"
                  items:
                    - uuid: item-1
                      name: "System name"
                      type: SNMP_AGENT
                      snmp_oid: get[1.3.6.1.2.1.1.5.0]
                      key: system.name
                      delay: 15m
                    - uuid: item-2
                      name: "SNMP walk interfaces"
                      type: SNMP_AGENT
                      snmp_oid: walk[1.3.6.1.2.1.31.1.1.1.1,1.3.6.1.2.1.31.1.1.1.18]
                      key: net.if.walk
                      value_type: TEXT
                  discovery_rules:
                    - uuid: rule-1
                      name: "Interfaces"
                      type: SNMP_AGENT
                      snmp_oid: discovery[{#IFNAME},1.3.6.1.2.1.31.1.1.1.1]
                      key: if.discovery
                      item_prototypes:
                        - uuid: p-1
                          name: "{#IFNAME}: in"
                          type: SNMP_AGENT
                          snmp_oid: 1.3.6.1.2.1.31.1.1.1.6.{#SNMPINDEX}
                          key: net.if.in[ifHCInOctets.{#SNMPINDEX}]
            """)
    );

    assertEquals("cisco-ios-snmp", preview.details().summary().id());
    assertTrue(preview.details().items().stream().anyMatch(item -> "net.if.walk".equals(item.key())));
    assertTrue(preview.details().discoveryRules().stream().anyMatch(rule -> "if.discovery".equals(rule.key())));
  }

  @Test
  void previewsDirectLinuxTemplateWithDependentDiscoveryLldPaths() {
    MonitoringTemplateImportPreviewDto preview = resolver.previewArchive(
        "template_os_linux_snmp_snmp.template",
        encodeTemplate("""
            zabbix_export:
              version: "8.0"
              templates:
                - template: "Linux by SNMP"
                  name: "Linux by SNMP"
                  items:
                    - uuid: item-master
                      name: "SNMP walk load"
                      type: SNMP_AGENT
                      snmp_oid: walk[1.3.6.1.4.1.2021.10.1.2,1.3.6.1.4.1.2021.10.1.3]
                      key: system.cpu.load.walk
                      value_type: TEXT
                    - uuid: item-dependent
                      name: "Load average (1m avg)"
                      type: DEPENDENT
                      key: system.cpu.load.avg1[laLoad.1]
                      value_type: FLOAT
                      preprocessing:
                        - type: JSONPATH
                          parameters:
                            - "$[?(@.laName == 'Load-1')].laLoad.first()"
                      master_item:
                        key: system.cpu.load.walk
                  discovery_rules:
                    - uuid: rule-dependent
                      name: "FS discovery"
                      type: DEPENDENT
                      key: vfs.fs.discovery
                      master_item:
                        key: system.cpu.load.walk
                      lld_macro_paths:
                        - lld_macro: "{#FSNAME}"
                          path: $.dskPath
                        - lld_macro: "{#SNMPINDEX}"
                          path: $.index
                      item_prototypes:
                        - uuid: p-fs
                          name: "FS used"
                          type: DEPENDENT
                          key: vfs.fs.used[dskUsed.{#SNMPINDEX}]
                          value_type: FLOAT
                          preprocessing:
                            - type: JSONPATH
                              parameters:
                                - "$.dskPath"
                          master_item:
                            key: system.cpu.load.walk
            """)
    );

    assertEquals("linux-by-snmp", preview.details().summary().id());
    assertTrue(preview.details().items().stream().anyMatch(item -> item.key().contains("system.cpu.load.avg1")));
    assertNotNull(preview.details().coverage());
    assertTrue(
        preview.details().coverage().features().stream()
            .filter(feature -> "lld_macro_paths".equals(feature.key()))
            .findFirst()
            .orElseThrow()
            .runtimeSupported()
    );
  }

  private byte[] encodeTemplate(String yaml) {
    return obfuscator.encodeUtf8(yaml).getBytes(StandardCharsets.UTF_8);
  }
}
