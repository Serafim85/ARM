package com.networkscanner.backend.monitoring.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses net-snmp {@code snmpwalk} text output ({@code .oid = TYPE: value}).
 */
public final class SnmpWalkDumpParser {

  private static final Pattern LINE =
      Pattern.compile("^\\.?(\\d+(?:\\.\\d+)*)\\s*=\\s*(?:\\w+:\\s*)?(.*)$");

  private SnmpWalkDumpParser() {
  }

  public static Map<String, String> parse(Path dumpFile) throws IOException {
    Map<String, String> values = new LinkedHashMap<>();
    for (String line : Files.readAllLines(dumpFile, StandardCharsets.UTF_8)) {
      parseLine(line, values);
    }
    return Map.copyOf(values);
  }

  public static Map<String, String> parseLines(Iterable<String> lines) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String line : lines) {
      parseLine(line, values);
    }
    return Map.copyOf(values);
  }

  private static void parseLine(String line, Map<String, String> values) {
    if (line == null || line.isBlank()) {
      return;
    }
    Matcher matcher = LINE.matcher(line.trim());
    if (!matcher.matches()) {
      return;
    }
    String oid = matcher.group(1);
    String rawValue = matcher.group(2) == null ? "" : matcher.group(2).trim();
    values.put(oid, sanitizeValue(rawValue));
  }

  static String sanitizeValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String value = raw.trim();
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      return value.substring(1, value.length() - 1);
    }
    if (value.startsWith("OID: ")) {
      return value.substring(5).trim();
    }
    if (value.startsWith("Hex-STRING:")) {
      return value.substring("Hex-STRING:".length()).trim();
    }
    if (value.startsWith("Timeticks:")) {
      int open = value.indexOf('(');
      int close = value.indexOf(')');
      if (open >= 0 && close > open) {
        return value.substring(open + 1, close).trim();
      }
    }
    int colon = value.indexOf(':');
    if (colon > 0 && colon < 24) {
      String prefix = value.substring(0, colon);
      if (prefix.matches("[A-Za-z][A-Za-z0-9-]*")) {
        return value.substring(colon + 1).trim();
      }
    }
    return value;
  }
}
