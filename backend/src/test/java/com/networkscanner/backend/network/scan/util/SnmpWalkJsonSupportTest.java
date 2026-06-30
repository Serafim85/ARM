package com.networkscanner.backend.network.scan.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnmpWalkJsonSupportTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void parseSnmpWalkToJsonTriplets_skipsIncompleteGroups() {
    assertThat(SnmpWalkJsonSupport.parseSnmpWalkToJsonTriplets(List.of("a", "1.2", "0", "{#X}", "", "0"))).hasSize(1);
  }

  @Test
  void jsonCellToString_handlesValueAndObjectNodes() throws Exception {
    JsonNode tree = MAPPER.readTree("{\"n\":42,\"o\":{\"k\":1}}");
    assertThat(SnmpWalkJsonSupport.jsonCellToString(tree.get("n"))).isEqualTo("42");
    assertThat(SnmpWalkJsonSupport.jsonCellToString(tree.get("o"))).contains("k");
    assertThat(SnmpWalkJsonSupport.jsonCellToString(null)).isNull();
  }
}
