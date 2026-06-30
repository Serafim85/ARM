package com.networkscanner.backend.monitoring.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ZabbixTemplateMacroSupportTest {

  @Test
  void appliesContextualMacroUsingBaseFallback() {
    Map<String, String> macros = Map.of("{$IF.UTIL.MAX}", "90");
    String resolved = ZabbixTemplateMacroSupport.applyTemplateMacros(
        "avg(...)>{$IF.UTIL.MAX:\"{#IFNAME}\"}",
        macros
    );
    assertEquals("avg(...)>90", resolved);
  }

  @Test
  void doesNotTreatResolvedNumericThresholdAsUnresolvedMacro() {
    assertFalse(ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(
        "last(/Template/item)>90"
    ));
  }

  @Test
  void detectsUnresolvedTemplateMacroReference() {
    assertTrue(ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(
        "last(/Template/item)>{$UNKNOWN.MACRO}"
    ));
  }
}
