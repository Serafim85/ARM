package com.networkscanner.backend.monitoring.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds Zabbix-style walk JSON payloads ({@code [{index, colN|alias, ...}]}) from a flat OID map.
 */
public final class SnmpWalkFixtureBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SnmpWalkFixtureBuilder() {
  }

  public static String buildWalkJson(Map<String, String> oidValues, List<String> columnOids) {
    if (columnOids == null || columnOids.isEmpty()) {
      return "[]";
    }
    Map<String, Map<String, String>> columnsBySuffix = new LinkedHashMap<>();
    Set<String> suffixes = new LinkedHashSet<>();
    Map<String, String> scalarFields = new LinkedHashMap<>();

    for (int i = 0; i < columnOids.size(); i++) {
      String columnOid = columnOids.get(i);
      String fieldName = LinuxBySnmpWalkSpecs.fieldNameForColumn(columnOid, i);
      Map<String, String> tableValues = tableValuesForColumn(oidValues, columnOid);
      if (tableValues.isEmpty()) {
        String scalar = oidValues.get(columnOid);
        if (scalar != null && !scalar.isBlank()) {
          scalarFields.put(fieldName, scalar);
        }
        continue;
      }
      for (Map.Entry<String, String> entry : tableValues.entrySet()) {
        suffixes.add(entry.getKey());
        columnsBySuffix
            .computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>())
            .put(fieldName, entry.getValue());
      }
    }

    if (!scalarFields.isEmpty() && !suffixes.isEmpty()) {
      for (String suffix : suffixes) {
        Map<String, String> row = columnsBySuffix.computeIfAbsent(suffix, ignored -> new LinkedHashMap<>());
        for (Map.Entry<String, String> scalar : scalarFields.entrySet()) {
          row.putIfAbsent(scalar.getKey(), scalar.getValue());
        }
      }
    }

    if (suffixes.isEmpty()) {
      return "[]";
    }

    List<Map<String, String>> rows = suffixes.stream()
        .sorted(SnmpWalkFixtureBuilder::compareSuffixes)
        .map(suffix -> {
          Map<String, String> row = new LinkedHashMap<>();
          row.put("index", suffix);
          Map<String, String> values = columnsBySuffix.get(suffix);
          if (values != null) {
            row.putAll(values);
          }
          return row;
        })
        .toList();

    try {
      return MAPPER.writeValueAsString(rows);
    } catch (JsonProcessingException exception) {
      return "[]";
    }
  }

  public static Map<String, String> buildWalkByItemKey(Map<String, String> oidValues) {
    Map<String, String> walks = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : LinuxBySnmpWalkSpecs.allWalkItems().entrySet()) {
      walks.put(entry.getKey(), buildWalkJson(oidValues, entry.getValue()));
    }
    return walks;
  }

  /**
   * wisla42 dump may omit UCD-SNMP-MIB tables ({@code 2021.9}, {@code 2021.10}); keep CI fixtures usable.
   */
  public static void mergeSyntheticWalkFallbacks(Map<String, String> walkByKey) {
    for (Map.Entry<String, String> entry : syntheticWalkFallbacks().entrySet()) {
      String current = walkByKey.get(entry.getKey());
      if (current == null || current.isBlank() || "[]".equals(current.trim())) {
        walkByKey.put(entry.getKey(), entry.getValue());
      }
    }
  }

  public static Map<String, String> syntheticWalkFallbacks() {
    Map<String, String> synthetic = new LinkedHashMap<>();
    synthetic.put(
        LinuxBySnmpWalkSpecs.CPU_LOAD_WALK_KEY,
        "[{\"index\":\"1\",\"laName\":\"Load-1\",\"laLoad\":\"0.42\"},{\"index\":\"2\",\"laName\":\"Load-5\",\"laLoad\":\"0.21\"},{\"index\":\"3\",\"laName\":\"Load-15\",\"laLoad\":\"0.10\"}]"
    );
    synthetic.put(
        LinuxBySnmpWalkSpecs.VFS_FS_WALK_KEY,
        "[{\"index\":\"1\",\"dskPath\":\"/\",\"dskDevice\":\"/dev/sda1\",\"col4\":\"ext4\",\"col5\":\"4068056\",\"col6\":\"3393600\",\"col7\":\"671456\",\"col8\":\"57755328\",\"col9\":\"57755328\",\"col10\":\"45\"},{\"index\":\"2\",\"dskPath\":\"/boot\",\"dskDevice\":\"/dev/sda2\",\"col4\":\"ext4\",\"col5\":\"204800\",\"col6\":\"167936\",\"col7\":\"36864\",\"col8\":\"1048576\",\"col9\":\"1048576\",\"col10\":\"22\"}]"
    );
    return synthetic;
  }

  public static Map<String, String> buildGetByItemKey(Map<String, String> oidValues) {
    Map<String, String> gets = new LinkedHashMap<>();
    String hostname = oidValues.get(LinuxBySnmpWalkSpecs.GET_SYSTEM_NAME_OID);
    if (hostname != null) {
      gets.put(LinuxBySnmpWalkSpecs.GET_SYSTEM_NAME_KEY, hostname);
    }
    return gets;
  }

  public static Map<String, String> filterOidMap(Map<String, String> fullDump, Iterable<String> prefixes) {
    Map<String, String> filtered = new TreeMap<>();
    for (Map.Entry<String, String> entry : fullDump.entrySet()) {
      String oid = entry.getKey();
      for (String prefix : prefixes) {
        if (oid.equals(prefix) || oid.startsWith(prefix + ".")) {
          filtered.put(oid, entry.getValue());
          break;
        }
      }
    }
    return filtered;
  }

  public static List<String> requiredOidPrefixes() {
    List<String> prefixes = new ArrayList<>();
    prefixes.add("1.3.6.1.2.1.1");
    prefixes.add("1.3.6.1.2.1.2");
    prefixes.add("1.3.6.1.2.1.25");
    prefixes.add("1.3.6.1.2.1.31");
    prefixes.add("1.3.6.1.4.1.2021");
    return prefixes;
  }

  private static Map<String, String> tableValuesForColumn(Map<String, String> oidValues, String columnOid) {
    Map<String, String> results = new LinkedHashMap<>();
    String prefix = columnOid + ".";
    for (Map.Entry<String, String> entry : oidValues.entrySet()) {
      String oid = entry.getKey();
      if (!oid.startsWith(prefix)) {
        continue;
      }
      String suffix = oid.substring(prefix.length());
      if (suffix.isBlank() || suffix.contains(".")) {
        continue;
      }
      results.put(suffix, entry.getValue());
    }
    return results;
  }

  private static int compareSuffixes(String left, String right) {
    try {
      return Comparator.comparingLong((String value) -> Long.parseLong(value.replace(".", "")))
          .compare(left, right);
    } catch (RuntimeException exception) {
      return left.compareTo(right);
    }
  }
}
