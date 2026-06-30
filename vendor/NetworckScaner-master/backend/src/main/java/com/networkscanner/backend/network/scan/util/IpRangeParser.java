package com.networkscanner.backend.network.scan.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IpRangeParser {

  public static final String SUBNET_FORMAT_ERROR =
      "Неверный формат подсети. Используйте 192.168.1.0-255 или 192.168.1.0/24";

  public static final String SUBNET_SCOPE_ERROR =
      "Слишком большая подсеть. Допускается маска /24–/32 или диапазон последнего октета (например, 192.168.1.0-255).";

  private static final int MIN_CIDR_PREFIX = 24;
  private static final int MAX_CIDR_PREFIX = 32;

  private static final Pattern RANGE_PATTERN =
      Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.(\\d{1,3})-(\\d{1,3})$");

  private static final Pattern CIDR_PATTERN =
      Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");

  public String normalizeSubnetRange(String subnetRange) {
    String trimmed = subnetRange.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(SUBNET_FORMAT_ERROR);
    }

    Matcher cidrMatcher = CIDR_PATTERN.matcher(trimmed);
    if (cidrMatcher.matches()) {
      return normalizeCidr(cidrMatcher.group(1), Integer.parseInt(cidrMatcher.group(2)));
    }

    Matcher rangeMatcher = RANGE_PATTERN.matcher(trimmed);
    if (!rangeMatcher.matches()) {
      throw new IllegalArgumentException(SUBNET_FORMAT_ERROR);
    }

    String base = rangeMatcher.group(1);
    int start = Integer.parseInt(rangeMatcher.group(2));
    int end = Integer.parseInt(rangeMatcher.group(3));
    validateLastOctetRange(base, start, end);
    return trimmed;
  }

  public List<String> expandRange(String subnetRange) {
    String normalized = normalizeSubnetRange(subnetRange);

    Matcher matcher = RANGE_PATTERN.matcher(normalized);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(SUBNET_FORMAT_ERROR);
    }

    String base = matcher.group(1);
    int start = Integer.parseInt(matcher.group(2));
    int end = Integer.parseInt(matcher.group(3));

    List<String> addresses = new ArrayList<>();
    for (int host = start; host <= end; host++) {
      if (host == 0 || host == 255) {
        continue;
      }
      addresses.add(base + "." + host);
    }
    return addresses;
  }

  private String normalizeCidr(String ip, int prefix) {
    parseIpv4(ip);
    if (prefix < MIN_CIDR_PREFIX || prefix > MAX_CIDR_PREFIX) {
      throw new IllegalArgumentException(SUBNET_SCOPE_ERROR);
    }
    String normalized = cidrToLastOctetRange(ip, prefix);
    if (normalized == null) {
      throw new IllegalArgumentException(SUBNET_SCOPE_ERROR);
    }
    return normalized;
  }

  private String cidrToLastOctetRange(String ip, int prefix) {
    long ipValue = parseIpv4(ip);
    long mask = prefix == 0 ? 0 : (-1L << (32 - prefix)) & 0xFFFFFFFFL;
    long network = ipValue & mask;
    long broadcast = network | (~mask & 0xFFFFFFFFL);

    int[] networkOctets = toOctets(network);
    int[] broadcastOctets = toOctets(broadcast);
    if (networkOctets[0] != broadcastOctets[0]
        || networkOctets[1] != broadcastOctets[1]
        || networkOctets[2] != broadcastOctets[2]) {
      return null;
    }

    return networkOctets[0]
        + "."
        + networkOctets[1]
        + "."
        + networkOctets[2]
        + "."
        + networkOctets[3]
        + "-"
        + broadcastOctets[3];
  }

  private void validateLastOctetRange(String base, int start, int end) {
    if (start < 0 || start > 255 || end < 0 || end > 255 || start > end) {
      throw new IllegalArgumentException("Границы диапазона должны быть в пределах 0-255.");
    }
    validateBase(base);
  }

  private long parseIpv4(String ip) {
    String[] parts = ip.split("\\.");
    if (parts.length != 4) {
      throw new IllegalArgumentException(SUBNET_FORMAT_ERROR);
    }

    long value = 0;
    for (String part : parts) {
      int octet = Integer.parseInt(part);
      if (octet < 0 || octet > 255) {
        throw new IllegalArgumentException(SUBNET_FORMAT_ERROR);
      }
      value = (value << 8) | octet;
    }
    return value & 0xFFFFFFFFL;
  }

  private int[] toOctets(long value) {
    return new int[] {
      (int) ((value >> 24) & 0xFF),
      (int) ((value >> 16) & 0xFF),
      (int) ((value >> 8) & 0xFF),
      (int) (value & 0xFF)
    };
  }

  private void validateBase(String base) {
    String[] octets = base.split("\\.");
    for (String octet : octets) {
      int value = Integer.parseInt(octet);
      if (value < 0 || value > 255) {
        throw new IllegalArgumentException("IP-адрес должен содержать октеты в диапазоне 0-255.");
      }
    }
  }
}
