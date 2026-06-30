package com.networkscanner.backend.network.scan.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for Zabbix-style SNMP walk preprocessing that materializes JSON rows.
 */
public final class SnmpWalkJsonSupport {

  private SnmpWalkJsonSupport() {
  }

  public static List<String[]> parseSnmpWalkToJsonTriplets(List<String> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return List.of();
    }
    List<String[]> list = new ArrayList<>();
    for (int i = 0; i + 2 < parameters.size(); i += 3) {
      String macro = parameters.get(i) == null ? "" : parameters.get(i).trim();
      String oid = parameters.get(i + 1) == null ? "" : parameters.get(i + 1).trim();
      String def = parameters.get(i + 2) == null ? "0" : parameters.get(i + 2).trim();
      if (macro.isBlank() || oid.isBlank()) {
        continue;
      }
      list.add(new String[] {macro, oid, def.isEmpty() ? "0" : def});
    }
    return list;
  }

  public static String jsonCellToString(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    return node.isValueNode() ? node.asText() : node.toString();
  }
}
