package com.networkscanner.backend.workstation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmWorkstationEventSupportTest {

  @Test
  void normalizeEventType_uppercasesKnownValues() {
    assertEquals("BSOD", ArmWorkstationEventSupport.normalizeEventType("bsod"));
    assertEquals("KERNEL_PANIC", ArmWorkstationEventSupport.normalizeEventType(" kernel_panic "));
  }

  @Test
  void normalizeSeverity_defaultsHighForBsod() {
    assertEquals("HIGH", ArmWorkstationEventSupport.normalizeSeverity(null, "BSOD"));
    assertEquals("WARNING", ArmWorkstationEventSupport.normalizeSeverity(null, "SERVICE_STOP"));
    assertEquals("AVERAGE", ArmWorkstationEventSupport.normalizeSeverity("average", "SERVICE_STOP"));
  }
}
