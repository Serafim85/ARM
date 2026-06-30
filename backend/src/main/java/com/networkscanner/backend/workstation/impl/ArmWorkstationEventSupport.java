package com.networkscanner.backend.workstation.impl;

final class ArmWorkstationEventSupport {

  static final String TYPE_BSOD = "BSOD";
  static final String TYPE_KERNEL_PANIC = "KERNEL_PANIC";

  private ArmWorkstationEventSupport() {
  }

  static String normalizeEventType(String raw) {
    if (raw == null || raw.isBlank()) {
      return "UNKNOWN";
    }
    return raw.trim().toUpperCase();
  }

  static String normalizeSeverity(String raw, String eventType) {
    if (raw != null && !raw.isBlank()) {
      return raw.trim().toUpperCase();
    }
    if (TYPE_BSOD.equals(eventType) || TYPE_KERNEL_PANIC.equals(eventType)) {
      return "HIGH";
    }
    return "WARNING";
  }
}
