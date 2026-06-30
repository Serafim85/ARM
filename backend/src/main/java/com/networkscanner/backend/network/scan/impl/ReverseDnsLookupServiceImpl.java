package com.networkscanner.backend.network.scan.impl;

import com.networkscanner.backend.network.scan.api.ReverseDnsLookupService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Service;

@Service
public class ReverseDnsLookupServiceImpl implements ReverseDnsLookupService {

  @Override
  public String lookup(String ip) {
    if (ip == null || ip.isBlank()) {
      return "-";
    }
    try {
      InetAddress address = InetAddress.getByName(ip.trim());
      String canonical = address.getCanonicalHostName();
      if (canonical == null || canonical.isBlank()) {
        return "-";
      }
      canonical = canonical.trim();
      if (isIpLiteral(canonical, ip.trim(), address)) {
        return "-";
      }
      return canonical;
    } catch (UnknownHostException exception) {
      return "-";
    }
  }

  /**
   * Detects unresolved PTR where JVM returns the IP itself, not a forward DNS match
   * (a valid PTR hostname normally resolves back to the same address).
   */
  private static boolean isIpLiteral(String host, String ip, InetAddress address) {
    String normalizedHost = host.toLowerCase();
    if (normalizedHost.equals(ip.toLowerCase())) {
      return true;
    }
    String hostAddress = address.getHostAddress();
    if (hostAddress != null && normalizedHost.equals(hostAddress.toLowerCase())) {
      return true;
    }
    return normalizedHost.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
  }
}
