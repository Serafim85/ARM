package com.networkscanner.backend.network.scan.util;

import com.networkscanner.backend.network.scan.dto.SnmpDeviceType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Определяет тип сетевого устройства по SNMP OID sysServices (1.3.6.1.2.1.1.7.0)
 * и ipForwarding (1.3.6.1.2.1.4.1.0) и возвращает тег для группировки.
 */
public final class SnmpDeviceTypeClassifier {

  private static final int LAYER_DATALINK = 2;
  private static final int LAYER_INTERNET = 4;
  private static final int LAYER_HOST = 8 | 16 | 32 | 64;

  private static final Pattern INTEGER_IN_PARENTHESES = Pattern.compile("\\((-?\\d+)\\)");

  private static final Set<String> DEVICE_TYPE_TAGS = Set.of(
      SnmpDeviceType.ROUTER.tag(),
      SnmpDeviceType.SWITCH.tag(),
      SnmpDeviceType.HOST.tag(),
      SnmpDeviceType.HYBRID.tag()
  );

  private SnmpDeviceTypeClassifier() {
  }

  public static Optional<SnmpDeviceType> classify(Integer sysServices, Integer ipForwarding) {
    if (sysServices == null && ipForwarding == null) {
      return Optional.empty();
    }

    boolean forwarding = ipForwarding != null && ipForwarding == 1;
    boolean datalink = sysServices != null && (sysServices & LAYER_DATALINK) != 0;
    boolean internet = sysServices != null && (sysServices & LAYER_INTERNET) != 0;
    boolean hostLayers = sysServices != null && (sysServices & LAYER_HOST) != 0;

    if (forwarding && datalink) {
      return Optional.of(SnmpDeviceType.HYBRID);
    }
    if (hostLayers && !forwarding && !datalink) {
      return Optional.of(SnmpDeviceType.HOST);
    }
    if (datalink && !forwarding) {
      return Optional.of(SnmpDeviceType.SWITCH);
    }
    if (forwarding || internet) {
      return Optional.of(SnmpDeviceType.ROUTER);
    }
    if (hostLayers || (ipForwarding != null && ipForwarding == 2)) {
      return Optional.of(SnmpDeviceType.HOST);
    }
    return Optional.empty();
  }

  public static List<String> resolveTags(Map<String, String> snmpValues) {
    if (snmpValues == null || snmpValues.isEmpty()) {
      return List.of();
    }
    return classify(
        parseSnmpInteger(snmpValues.get("sysServices")),
        parseSnmpInteger(snmpValues.get("ipForwarding"))
    )
        .map(type -> List.of(type.tag()))
        .orElse(List.of());
  }

  public static boolean isDeviceTypeTag(String tag) {
    return tag != null && DEVICE_TYPE_TAGS.contains(tag.trim());
  }

  public static List<String> mergeDeviceTypeTag(List<String> existingTags, List<String> scanTags) {
    String newTypeTag = null;
    if (scanTags != null) {
      for (String tag : scanTags) {
        if (isDeviceTypeTag(tag)) {
          newTypeTag = tag.trim();
          break;
        }
      }
    }

    LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (existingTags != null) {
      for (String tag : existingTags) {
        if (tag == null || tag.isBlank() || isDeviceTypeTag(tag)) {
          continue;
        }
        merged.add(tag.trim());
      }
    }
    if (newTypeTag != null) {
      merged.add(newTypeTag);
    }
    return List.copyOf(merged);
  }

  static Integer parseSnmpInteger(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    try {
      return Integer.parseInt(trimmed);
    } catch (NumberFormatException ignored) {
      Matcher matcher = INTEGER_IN_PARENTHESES.matcher(trimmed);
      if (matcher.find()) {
        try {
          return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignoredAgain) {
          return null;
        }
      }
      return null;
    }
  }
}
