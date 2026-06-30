package com.networkscanner.backend.monitoring.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.UploadedMonitoringTemplatePackage;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.monitoring.util.LinuxBySnmpWalkSpecs;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/**
 * Shared harness: resolves bundled {@code linux-by-snmp} and stubs SNMP from classpath fixtures.
 */
public final class LinuxBySnmpFixtureSupport {

  public static final String LINUX_TEMPLATE_RESOURCE =
      "monitoring-templates/os/linux_snmp_snmp/template_os_linux_snmp_snmp.template";
  public static final String FIXTURE_BASE = "fixtures/snmp/linux-by-snmp-wisla42/";
  public static final String DEVICE_IP = "10.0.0.42";
  public static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-05-25T12:00:00Z");

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static volatile ResolvedMonitoringTemplate cachedTemplate;
  private static volatile Map<String, String> cachedGet;
  private static volatile Map<String, String> cachedWalk;
  private static volatile JsonNode cachedScenarios;
  private static volatile SnmpScanService cachedSnmp;

  /** Clears static caches between tests that mutate fixture-backed SNMP stubs. */
  static void resetCaches() {
    cachedTemplate = null;
    cachedGet = null;
    cachedWalk = null;
    cachedSnmp = null;
    cachedScenarios = null;
  }

  private LinuxBySnmpFixtureSupport() {
  }

  public static MonitoringTemplateResolver resolver() {
    UploadedMonitoringTemplateRepository uploadedRepository = mock(UploadedMonitoringTemplateRepository.class);
    AppUserRepository appUserRepository = mock(AppUserRepository.class);
    org.mockito.Mockito.when(uploadedRepository.findAllByOrderByTemplateIdAsc())
        .thenReturn(List.of(linuxUploadedEntity()));
    MonitoringTemplateObfuscator obfuscator = new MonitoringTemplateObfuscator();
    MonitoringTemplateResolverImpl resolver = new MonitoringTemplateResolverImpl(
        MAPPER,
        uploadedRepository,
        org.mockito.Mockito.mock(
            com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class
        ),
        appUserRepository,
        new MonitoringTemplateArchiveReader(obfuscator),
        obfuscator
    );
    resolver.initialize();
    return resolver;
  }

  public static String templateId() {
    return linuxUploadedEntity().getTemplateId();
  }

  public static ResolvedMonitoringTemplate template() {
    if (cachedTemplate == null) {
      cachedTemplate = resolver().resolveTemplateById(templateId());
    }
    return cachedTemplate;
  }

  private static UploadedMonitoringTemplateEntity linuxUploadedEntity() {
    try {
      MonitoringTemplateObfuscator obfuscator = new MonitoringTemplateObfuscator();
      MonitoringTemplateArchiveReader reader = new MonitoringTemplateArchiveReader(obfuscator);
      byte[] bytes;
      try (InputStream in = new ClassPathResource(LINUX_TEMPLATE_RESOURCE).getInputStream()) {
        bytes = in.readAllBytes();
      }
      UploadedMonitoringTemplatePackage uploaded = reader.readSingleTemplatePackage(
          "template_os_linux_snmp_snmp.template",
          bytes
      );
      UploadedMonitoringTemplateEntity entity = new UploadedMonitoringTemplateEntity();
      entity.setTemplateId(uploaded.templateId());
      entity.setManifestYaml(uploaded.manifestYaml());
      entity.setTemplateYaml(uploaded.templateYaml());
      entity.setTemplateFileName(uploaded.templateFileName());
      entity.setOriginalFilename("template_os_linux_snmp_snmp.template");
      return entity;
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to load linux SNMP template for tests", exception);
    }
  }

  public static Map<String, String> getFixtures() {
    if (cachedGet == null) {
      cachedGet = readJsonMap(FIXTURE_BASE + "get-by-item-key.json");
    }
    return cachedGet;
  }

  public static Map<String, String> walkFixtures() {
    if (cachedWalk == null) {
      cachedWalk = readJsonMap(FIXTURE_BASE + "walk-by-item-key.json");
    }
    return cachedWalk;
  }

  public static JsonNode triggerScenarios() {
    if (cachedScenarios == null) {
      try (InputStream in = new ClassPathResource(FIXTURE_BASE + "trigger-scenarios.json").getInputStream()) {
        cachedScenarios = MAPPER.readTree(in);
      } catch (Exception exception) {
        throw new IllegalStateException("Failed to load trigger-scenarios.json", exception);
      }
    }
    return cachedScenarios;
  }

  public static MonitoredDeviceEntity device() {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setIp(DEVICE_IP);
    entity.setTemplateId(templateId());
    entity.setEffectiveTemplateId(templateId());
    return entity;
  }

  public static SnmpScanService snmpServiceFromFixtures() {
    if (cachedSnmp != null) {
      return cachedSnmp;
    }
    Map<String, String> get = getFixtures();
    Map<String, String> walk = walkFixtures();
    SnmpScanService snmp = mock(SnmpScanService.class);
    doAnswer(invocation -> {
      Map<String, String> requested = invocation.getArgument(2);
      Map<String, String> responses = new LinkedHashMap<>();
      for (Map.Entry<String, String> entry : requested.entrySet()) {
        String key = entry.getKey();
        if (get.containsKey(key)) {
          responses.put(key, get.get(key));
        } else if (walk.containsKey(key)) {
          responses.put(key, walk.get(key));
        }
      }
      return responses;
    }).when(snmp).readRawOids(any(), any(), any());
    doAnswer(invocation -> {
      ResolvedMonitoringTemplate tpl = invocation.getArgument(1);
      ZabbixDiscoveryRuleRuntime rule = invocation.getArgument(2);
      OffsetDateTime ts = invocation.getArgument(3);
      if (!rule.isDependent() || rule.masterItemKey() == null) {
        return List.of();
      }
      return LinuxBySnmpDependentDiscoveryRunner.execute(tpl, rule, get, walk, ts);
    }).when(snmp).executeDiscovery(any(), any(), any(), any());
    cachedSnmp = snmp;
    return cachedSnmp;
  }

  public static Map<String, List<DiscoveryInstanceRuntime>> discoverAll(SnmpScanService snmp) {
    ResolvedMonitoringTemplate template = template();
    Map<String, List<DiscoveryInstanceRuntime>> instances = new LinkedHashMap<>();
    for (ZabbixDiscoveryRuleRuntime rule : template.discoveryRules().values()) {
      if (!rule.isDependent()) {
        continue;
      }
      if (!List.of("net.if.discovery", "vfs.fs.discovery[snmp]", "cpu.discovery[snmp]").contains(rule.key())) {
        continue;
      }
      List<DiscoveryInstanceRuntime> discovered = snmp.executeDiscovery(
          DEVICE_IP,
          template,
          rule,
          TIMESTAMP
      );
      instances.put(rule.key(), discovered);
    }
    return instances;
  }

  public static DiscoveryInstanceRuntime findNetIfInstance(String ifName) {
    Map<String, List<DiscoveryInstanceRuntime>> all = discoverAll(snmpServiceFromFixtures());
    return all.getOrDefault("net.if.discovery", List.of()).stream()
        .filter(instance -> ifName.equals(instance.macros().get("{#IFNAME}")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Interface not discovered: " + ifName));
  }

  public static MaterializedZabbixItem materialize(
      ZabbixItemRuntime runtime,
      String itemKey,
      String instanceKey,
      String discoveryRuleKey,
      Map<String, String> macros
  ) {
    String oid = runtime.snmpOid() == null ? null : applyMacros(runtime.snmpOid(), macros);
    return new MaterializedZabbixItem(
        templateId(),
        runtime,
        itemKey,
        itemKey,
        instanceKey == null ? "" : instanceKey,
        discoveryRuleKey,
        oid,
        macros == null ? Map.of() : macros
    );
  }

  public static MaterializedZabbixItem materializePrototype(
      String prototypeKey,
      DiscoveryInstanceRuntime instance
  ) {
    ResolvedMonitoringTemplate template = template();
    ZabbixDiscoveryRuleRuntime rule = template.discoveryRule(instance.discoveryRuleKey());
    ZabbixItemRuntime prototype = rule.itemPrototypes().stream()
        .filter(item -> item.key() != null && item.key().contains(prototypeKey))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Prototype not found: " + prototypeKey + " in " + rule.key()));
    String itemKey = applyMacros(prototype.key(), instance.macros());
    return materialize(
        prototype,
        itemKey,
        instance.instanceKey(),
        instance.discoveryRuleKey(),
        instance.macros()
    );
  }

  public static List<ZabbixItemValue> collectBaselineMetrics() {
    ResolvedMonitoringTemplate template = template();
    SnmpScanService snmp = snmpServiceFromFixtures();
    MonitoringPreprocessingEngine preprocessingEngine = new MonitoringPreprocessingEngine();
    SnmpMonitoringItemExecutor snmpExecutor = new SnmpMonitoringItemExecutor(snmp, preprocessingEngine);
    DerivedMonitoringItemExecutor derivedExecutor = new DerivedMonitoringItemExecutor(
        preprocessingEngine,
        mock(com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService.class),
        snmp
    );

    List<MaterializedZabbixItem> snmpItems = new ArrayList<>();
    snmpItems.add(materialize(
        template.items().get(LinuxBySnmpWalkSpecs.GET_SYSTEM_NAME_KEY),
        LinuxBySnmpWalkSpecs.GET_SYSTEM_NAME_KEY,
        "",
        null,
        Map.of()
    ));
    snmpItems.add(materialize(
        template.items().get(LinuxBySnmpWalkSpecs.CPU_LOAD_WALK_KEY),
        LinuxBySnmpWalkSpecs.CPU_LOAD_WALK_KEY,
        "",
        null,
        Map.of()
    ));

    Map<String, ZabbixItemValue> cycle = new LinkedHashMap<>();
    snmpExecutor.execute(device(), template, snmpItems, Map.of(), cycle, TIMESTAMP)
        .forEach(value -> cycle.put(stateKey(value), value));

    List<MaterializedZabbixItem> derivedItems = new ArrayList<>();
    ZabbixItemRuntime loadAvg1 = template.items().get("system.cpu.load.avg1[laLoad.1]");
    if (loadAvg1 != null) {
      derivedItems.add(materialize(loadAvg1, "system.cpu.load.avg1[laLoad.1]", "", null, Map.of()));
    }
    DiscoveryInstanceRuntime ens18 = findNetIfInstance("ens18");
    derivedItems.add(materializePrototype("net.if.in[ifHCInOctets", ens18));
    derivedItems.add(materializePrototype("net.if.status[ifOperStatus", ens18));

    List<ZabbixItemValue> all = new ArrayList<>(cycle.values());
    all.addAll(derivedExecutor.execute(device(), template, derivedItems, Map.of(), cycle, TIMESTAMP));
    return all;
  }

  public static String stateKey(ZabbixItemValue value) {
    return stateKey(value.itemKey(), value.instanceKey());
  }

  public static String stateKey(String itemKey, String instanceKey) {
    String instance = instanceKey == null || instanceKey.isBlank() ? "" : instanceKey;
    return itemKey + "::" + instance;
  }

  public static String applyMacros(String template, Map<String, String> macros) {
    String result = template;
    for (Map.Entry<String, String> macro : macros.entrySet()) {
      result = result.replace(macro.getKey(), macro.getValue());
    }
    return result;
  }

  private static Map<String, String> readJsonMap(String classpathResource) {
    try (InputStream in = new ClassPathResource(classpathResource).getInputStream()) {
      return MAPPER.readValue(in, new TypeReference<LinkedHashMap<String, String>>() {});
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to load " + classpathResource, exception);
    }
  }
}
