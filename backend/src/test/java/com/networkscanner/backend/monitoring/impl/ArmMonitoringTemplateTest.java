package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifest;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifestEntry;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArmMonitoringTemplateTest {

  private static final Set<String> AGENT_ITEM_KEYS = Set.of(
      "arm.cpu.util",
      "arm.mem.used",
      "arm.disk.root.used_pct"
  );

  private MonitoringTemplateObfuscator obfuscator;
  private MonitoringTemplateResolverImpl resolver;
  private MonitoringTemplateManifest manifest;
  private Path templatesDir;

  @BeforeEach
  void setUp() throws Exception {
    obfuscator = new MonitoringTemplateObfuscator();
    resolver = new MonitoringTemplateResolverImpl(
        new ObjectMapper(),
        null,
        null,
        null,
        new MonitoringTemplateArchiveReader(obfuscator),
        obfuscator
    );
    templatesDir = Path.of("..", "templates").toAbsolutePath().normalize();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    yamlMapper.findAndRegisterModules();
    String manifestYaml = obfuscator.decodeUtf8(Files.readAllBytes(templatesDir.resolve("manifest.template")));
    manifest = yamlMapper.readValue(manifestYaml, MonitoringTemplateManifest.class);
  }

  @Test
  void loadsArmLinuxTemplateWithAgentItemKeys() throws Exception {
    ResolvedMonitoringTemplate template = compileTemplate("arm-linux");
    assertEquals("WISLA ARM Linux", template.name());
    assertEquals("AGENT", template.type());
    for (String itemKey : AGENT_ITEM_KEYS) {
      assertTrue(template.items().containsKey(itemKey), "missing item " + itemKey);
      assertEquals("ZABBIX_PASSIVE", template.items().get(itemKey).type());
    }
    assertFalse(template.triggers().isEmpty());
  }

  @Test
  void loadsArmWindowsTemplateWithAgentItemKeys() throws Exception {
    ResolvedMonitoringTemplate template = compileTemplate("arm-windows");
    assertEquals("WISLA ARM Windows", template.name());
    for (String itemKey : AGENT_ITEM_KEYS) {
      assertTrue(template.items().containsKey(itemKey), "missing item " + itemKey);
    }
  }

  @Test
  void resolvesCpuThresholdMacroInLinuxTemplate() throws Exception {
    ResolvedMonitoringTemplate template = compileTemplate("arm-linux");
    String cpuTriggerExpression = template.triggers().values().stream()
        .filter(trigger -> trigger.name().contains("High CPU"))
        .map(trigger -> trigger.expression())
        .findFirst()
        .orElseThrow();
    assertTrue(cpuTriggerExpression.contains(">80"));
    assertFalse(cpuTriggerExpression.contains("{$ARM.CPU.WARN}"));

    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        cpuTriggerExpression,
        OffsetDateTime.parse("2026-06-22T12:00:00Z"),
        (metricName, window, timestamp) -> List.of(85.0)
    );
    assertTrue(evaluation.breached());
  }

  private ResolvedMonitoringTemplate compileTemplate(String templateId) throws Exception {
    MonitoringTemplateManifestEntry entry = manifest.templates().stream()
        .filter(candidate -> templateId.equals(candidate.id()))
        .findFirst()
        .orElseThrow();
    String templateFile = entry.file().replace(".yaml", ".template");
    String templateYaml = obfuscator.decodeUtf8(Files.readAllBytes(templatesDir.resolve(templateFile)));
    Method compileTemplate = MonitoringTemplateResolverImpl.class.getDeclaredMethod(
        "compileTemplate",
        MonitoringTemplateManifest.class,
        MonitoringTemplateManifestEntry.class,
        String.class
    );
    compileTemplate.setAccessible(true);
    return (ResolvedMonitoringTemplate) compileTemplate.invoke(resolver, manifest, entry, templateYaml);
  }
}
