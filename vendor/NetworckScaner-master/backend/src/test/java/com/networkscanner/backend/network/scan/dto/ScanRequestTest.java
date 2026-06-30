package com.networkscanner.backend.network.scan.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanRequestTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void effectiveProbesFromLegacyScanMode() {
    ScanRequest request = legacyRequest("SNMP_V2", 161, "public");

    List<DiscoveryProbeConfig> probes = request.effectiveProbes();

    assertEquals(1, probes.size());
    assertEquals("SNMP_V2", probes.get(0).method());
    assertEquals(161, probes.get(0).port());
    assertEquals("public", probes.get(0).community());
  }

  @Test
  void effectiveProbesPrefersExplicitProbesList() {
    ScanRequest request = new ScanRequest(
        "192.168.1.0/24",
        "ICMP",
        "v2c",
        161,
        1500,
        1,
        "public",
        null,
        null,
        null,
        null,
        null,
        List.of(
            new DiscoveryProbeConfig("ICMP", null, null, null, null, null, null, null),
            new DiscoveryProbeConfig("SNMP_V2", 161, "private", null, null, null, null, null)
        )
    );

    List<DiscoveryProbeConfig> probes = request.effectiveProbes();

    assertEquals(2, probes.size());
    assertEquals("ICMP", probes.get(0).method());
    assertEquals("SNMP_V2", probes.get(1).method());
    assertEquals("private", probes.get(1).community());
  }

  @Test
  void preferredSnmpProbeChoosesV3OverV2() {
    ScanRequest request = new ScanRequest(
        "10.0.0.0/24",
        null,
        null,
        161,
        1500,
        1,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(
            new DiscoveryProbeConfig("ICMP", null, null, null, null, null, null, null),
            new DiscoveryProbeConfig("SNMP_V2", 161, "public", null, null, null, null, null),
            new DiscoveryProbeConfig("SNMP_V3", 161, null, "user", "SHA", "auth", "AES", "priv")
        )
    );

    DiscoveryProbeConfig preferred = request.preferredSnmpProbe();

    assertNotNull(preferred);
    assertEquals("SNMP_V3", preferred.method());
    assertEquals("user", preferred.securityUsername());
  }

  @Test
  void hasDiscoveryMethodValidation() {
    ScanRequest empty = new ScanRequest(
        "192.168.1.0/24", null, null, 161, 1500, 1,
        null, null, null, null, null, null, null
    );
    assertFalse(empty.hasDiscoveryMethod());

    ScanRequest legacy = legacyRequest("ICMP", 1, null);
    assertTrue(legacy.hasDiscoveryMethod());

    ScanRequest modern = new ScanRequest(
        "192.168.1.0/24", null, null, 161, 1500, 1,
        null, null, null, null, null, null,
        List.of(new DiscoveryProbeConfig("ICMP", null, null, null, null, null, null, null))
    );
    assertTrue(modern.hasDiscoveryMethod());
  }

  @Test
  void deserializesLegacyJsonWithoutProbes() throws Exception {
    String json = """
        {
          "subnetRange": "192.168.1.0/24",
          "scanMode": "SNMP_V2",
          "snmpVersion": "v2c",
          "port": 161,
          "timeout": 1500,
          "retries": 1,
          "community": "public"
        }
        """;

    ScanRequest request = objectMapper.readValue(json, ScanRequest.class);

    assertEquals("SNMP_V2", request.scanMode());
    assertEquals(1, request.effectiveProbes().size());
    assertNull(request.probes());
  }

  @Test
  void deserializesModernJsonWithProbes() throws Exception {
    String json = """
        {
          "subnetRange": "192.168.1.0/24",
          "timeout": 1500,
          "retries": 1,
          "port": 1,
          "probes": [
            { "method": "ICMP" },
            { "method": "TCP", "port": 22 }
          ]
        }
        """;

    ScanRequest request = objectMapper.readValue(json, ScanRequest.class);

    assertEquals(2, request.effectiveProbes().size());
    assertEquals(22, ScanRequest.resolveProbePort(request.effectiveProbes().get(1)));
    assertTrue(request.hasDiscoveryMethod());
  }

  @Test
  void deserializesAccessProfileId() throws Exception {
    String json = """
        {
          "subnetRange": "10.0.0.0-10",
          "timeout": 1500,
          "retries": 1,
          "port": 1,
          "accessProfileId": 9,
          "probes": [{ "method": "SNMP_V2", "port": 161 }]
        }
        """;

    ScanRequest request = objectMapper.readValue(json, ScanRequest.class);

    assertEquals(9L, request.accessProfileId());
    assertEquals("SNMP_V2", request.effectiveProbes().get(0).method());
  }

  @Test
  void snmpVersionForProbe() {
    assertEquals("v1", ScanRequest.snmpVersionForProbe(
        new DiscoveryProbeConfig("SNMP_V1", 161, "public", null, null, null, null, null)));
    assertEquals("v2c", ScanRequest.snmpVersionForProbe(
        new DiscoveryProbeConfig("SNMP_V2", 161, "public", null, null, null, null, null)));
    assertEquals("v3", ScanRequest.snmpVersionForProbe(
        new DiscoveryProbeConfig("SNMP_V3", 161, null, "u", null, null, null, null)));
  }

  private static ScanRequest legacyRequest(String scanMode, int port, String community) {
    return new ScanRequest(
        "192.168.1.0/24",
        scanMode,
        "v2c",
        port,
        1500,
        1,
        community,
        null,
        null,
        null,
        null,
        null,
        null
    );
  }
}
