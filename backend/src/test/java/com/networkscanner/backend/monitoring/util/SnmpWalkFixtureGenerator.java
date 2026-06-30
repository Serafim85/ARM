package com.networkscanner.backend.monitoring.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes golden fixture JSON under {@code src/test/resources/fixtures/snmp/linux-by-snmp-wisla42/}. */
public final class SnmpWalkFixtureGenerator {

  public static final String FIXTURE_DIR =
      "src/test/resources/fixtures/snmp/linux-by-snmp-wisla42";

  /** Bundled net-snmp dump from wisla42 (override via {@code -Dsnmp.walk.source=...}). */
  public static final String BUNDLED_SNMP_WALK_DUMP =
      FIXTURE_DIR + "/snmpwalk-wisla42.txt";

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT);

  private SnmpWalkFixtureGenerator() {
  }

  public static Path resolveBackendRoot(Path cwd) {
    Path backendRoot = cwd.toAbsolutePath().normalize();
    if (!Files.exists(backendRoot.resolve("pom.xml"))) {
      backendRoot = backendRoot.resolve("backend");
    }
    return backendRoot;
  }

  /**
   * {@code -Dsnmp.walk.source} if set and file exists; otherwise bundled {@link #BUNDLED_SNMP_WALK_DUMP}.
   */
  public static java.util.Optional<Path> resolveSnmpWalkSource(Path backendRoot) {
    String prop = System.getProperty("snmp.walk.source");
    if (prop != null && !prop.isBlank()) {
      Path custom = Path.of(prop);
      if (Files.exists(custom)) {
        return java.util.Optional.of(custom.toAbsolutePath().normalize());
      }
    }
    Path bundled = backendRoot.resolve(BUNDLED_SNMP_WALK_DUMP);
    if (Files.exists(bundled)) {
      return java.util.Optional.of(bundled);
    }
    return java.util.Optional.empty();
  }

  public static void generateFromDump(Path snmpWalkFile, Path outputDir) throws IOException {
    Map<String, String> full = SnmpWalkDumpParser.parse(snmpWalkFile);
    Map<String, String> oidValues =
        SnmpWalkFixtureBuilder.filterOidMap(full, SnmpWalkFixtureBuilder.requiredOidPrefixes());
    writeFixtures(oidValues, outputDir);
  }

  public static void writeFixtures(Map<String, String> oidValues, Path outputDir) throws IOException {
    Files.createDirectories(outputDir);

    Map<String, String> getByKey = SnmpWalkFixtureBuilder.buildGetByItemKey(oidValues);
    Map<String, String> walkByKey = SnmpWalkFixtureBuilder.buildWalkByItemKey(oidValues);
    SnmpWalkFixtureBuilder.mergeSyntheticWalkFallbacks(walkByKey);

    writeJson(outputDir.resolve("raw-oids.json"), oidValues);
    writeJson(outputDir.resolve("get-by-item-key.json"), getByKey);
    writeJson(outputDir.resolve("walk-by-item-key.json"), walkByKey);
    writeJson(outputDir.resolve("trigger-scenarios.json"), defaultTriggerScenarios());
  }

  private static Map<String, Object> defaultTriggerScenarios() {
    Map<String, Object> scenarios = new LinkedHashMap<>();

    Map<String, Object> snmpDown = new LinkedHashMap<>();
    snmpDown.put("description", "SNMP agent unavailable");
    snmpDown.put("expressionContains", "zabbix[host,snmp,available]");
    snmpDown.put("metricOverrides", Map.of("zabbix[host,snmp,available]", List.of(0.0)));
    snmpDown.put("expectBreached", true);
    scenarios.put("snmp_unavailable", snmpDown);

    Map<String, Object> linkDown = new LinkedHashMap<>();
    linkDown.put("lldRule", "net.if.discovery");
    linkDown.put("instanceKey", "2");
    linkDown.put("macros", Map.of("{#IFNAME}", "ens18", "{#SNMPINDEX}", "2"));
    linkDown.put("triggerNameContains", "Link down");
    linkDown.put("metricOverrides", Map.of("net.if.status[ifOperStatus.2]", List.of(2.0)));
    linkDown.put("templateMacroOverrides", Map.of("{$IFCONTROL:\"{#IFNAME}\"}", "1"));
    linkDown.put("expectBreached", true);
    scenarios.put("ens18_link_down", linkDown);

    Map<String, Object> linkRecovery = new LinkedHashMap<>();
    linkRecovery.put("lldRule", "net.if.discovery");
    linkRecovery.put("instanceKey", "2");
    linkRecovery.put("macros", Map.of("{#IFNAME}", "ens18", "{#SNMPINDEX}", "2"));
    linkRecovery.put("triggerNameContains", "Link down");
    linkRecovery.put("evaluateRecovery", true);
    linkRecovery.put("metricOverrides", Map.of("net.if.status[ifOperStatus.2]", List.of(1.0)));
    linkRecovery.put("templateMacroOverrides", Map.of("{$IFCONTROL:\"{#IFNAME}\"}", "1"));
    linkRecovery.put("expectBreached", false);
    scenarios.put("ens18_link_down_recovery", linkRecovery);

    Map<String, Object> bandwidth = new LinkedHashMap<>();
    bandwidth.put("lldRule", "net.if.discovery");
    bandwidth.put("instanceKey", "2");
    bandwidth.put("macros", Map.of("{#IFNAME}", "ens18", "{#SNMPINDEX}", "2"));
    bandwidth.put("triggerNameContains", "High bandwidth");
    bandwidth.put("historyOverrides", Map.of(
        "net.if.in[ifHCInOctets.2]", List.of(9_000_000_000.0, 9_000_000_000.0, 9_000_000_000.0),
        "net.if.out[ifHCOutOctets.2]", List.of(9_000_000_000.0, 9_000_000_000.0, 9_000_000_000.0),
        "net.if.speed[ifHighSpeed.2]", List.of(1000.0, 1000.0, 1000.0)
    ));
    bandwidth.put("expectBreached", true);
    scenarios.put("ens18_high_bandwidth", bandwidth);

    return scenarios;
  }

  private static void writeJson(Path path, Object value) throws IOException {
    Files.writeString(path, MAPPER.writeValueAsString(value) + System.lineSeparator(), StandardCharsets.UTF_8);
  }
}
