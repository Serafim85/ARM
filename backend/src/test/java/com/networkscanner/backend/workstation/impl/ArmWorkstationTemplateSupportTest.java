package com.networkscanner.backend.workstation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmWorkstationTemplateSupportTest {

  @Test
  void templateIdForOsType_windows() {
    assertEquals("arm-windows", ArmWorkstationTemplateSupport.templateIdForOsType("windows"));
    assertEquals("arm-windows", ArmWorkstationTemplateSupport.templateIdForOsType("Windows"));
  }

  @Test
  void templateIdForOsType_defaultsToLinux() {
    assertEquals("arm-linux", ArmWorkstationTemplateSupport.templateIdForOsType("linux"));
    assertEquals("arm-linux", ArmWorkstationTemplateSupport.templateIdForOsType("unknown"));
    assertEquals("arm-linux", ArmWorkstationTemplateSupport.templateIdForOsType(null));
  }
}
