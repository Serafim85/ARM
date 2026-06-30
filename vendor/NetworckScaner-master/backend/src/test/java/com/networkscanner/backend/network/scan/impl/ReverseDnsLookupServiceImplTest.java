package com.networkscanner.backend.network.scan.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReverseDnsLookupServiceImplTest {

  private final ReverseDnsLookupServiceImpl service = new ReverseDnsLookupServiceImpl();

  @Test
  void returnsDashForBlankIp() {
    assertEquals("-", service.lookup(null));
    assertEquals("-", service.lookup(""));
    assertEquals("-", service.lookup("   "));
  }

  @Test
  void returnsDashWhenPtrRecordIsMissing() {
    assertEquals("-", service.lookup("192.0.2.1"));
  }

  @Test
  void resolvesPtrWhenHostnameMapsBackToSameIp() {
    String domain = service.lookup("208.67.222.222");
    if ("-".equals(domain)) {
      domain = service.lookup("8.8.8.8");
    }
    org.junit.jupiter.api.Assumptions.assumeFalse("-".equals(domain),
        "PTR not resolvable in this environment");
    org.junit.jupiter.api.Assertions.assertFalse(domain.matches("^\\d{1,3}(\\.\\d{1,3}){3}$"));
  }
}
