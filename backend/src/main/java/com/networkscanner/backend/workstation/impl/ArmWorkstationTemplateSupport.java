package com.networkscanner.backend.workstation.impl;

final class ArmWorkstationTemplateSupport {

  static final String TAG_ARM_WORKSTATION = "arm-workstation";

  private ArmWorkstationTemplateSupport() {
  }

  static String templateIdForOsType(String osType) {
    if (osType != null && "windows".equalsIgnoreCase(osType.trim())) {
      return "arm-windows";
    }
    return "arm-linux";
  }
}
