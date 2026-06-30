package com.networkscanner.backend.monitoring.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class SnmpWalkFixtureGeneratorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void buildWalkJsonFromSampleLines() throws Exception {
    var lines = java.util.List.of(
        ".1.3.6.1.2.1.1.5.0 = STRING: \"wisla42\"",
        ".1.3.6.1.2.1.2.2.1.2.1 = STRING: \"lo\"",
        ".1.3.6.1.2.1.2.2.1.2.2 = STRING: \"ens18\"",
        ".1.3.6.1.2.1.2.2.1.8.1 = INTEGER: 1",
        ".1.3.6.1.2.1.2.2.1.8.2 = INTEGER: 1",
        ".1.3.6.1.2.1.31.1.1.1.1.2 = STRING: \"ens18\""
    );
    Map<String, String> oids = SnmpWalkDumpParser.parseLines(lines);
    String json = SnmpWalkFixtureBuilder.buildWalkJson(oids, LinuxBySnmpWalkSpecs.NET_IF_WALK_COLUMNS);
    JsonNode root = MAPPER.readTree(json);
    assertTrue(root.isArray());
    assertFalse(root.isEmpty());
    assertTrue(json.contains("ens18"));
  }

  @Test
  @EnabledIf("snmpWalkSourceConfigured")
  void regenerateGoldenFixturesFromDump() throws Exception {
    Path backendRoot = SnmpWalkFixtureGenerator.resolveBackendRoot(Paths.get(""));
    Path source = SnmpWalkFixtureGenerator.resolveSnmpWalkSource(backendRoot).orElseThrow();
    Path output = backendRoot.resolve(SnmpWalkFixtureGenerator.FIXTURE_DIR);
    SnmpWalkFixtureGenerator.generateFromDump(source, output);
    assertTrue(Files.exists(output.resolve("walk-by-item-key.json")));
  }

  /** Bundled {@link SnmpWalkFixtureGenerator#BUNDLED_SNMP_WALK_DUMP} or {@code -Dsnmp.walk.source}. */
  static boolean snmpWalkSourceConfigured() {
    Path backendRoot = SnmpWalkFixtureGenerator.resolveBackendRoot(Paths.get(""));
    return SnmpWalkFixtureGenerator.resolveSnmpWalkSource(backendRoot).isPresent();
  }
}
