package com.networkscanner.backend.workstation.impl;

import org.springframework.data.domain.Sort;

final class WorkstationSortSupport {

  private WorkstationSortSupport() {
  }

  static Sort buildSort(String sortField, String sortOrder) {
    String field = normalizeField(sortField);
    Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(direction, field);
  }

  private static String normalizeField(String sortField) {
    if (sortField == null || sortField.isBlank()) {
      return "lastSeenAt";
    }
    return switch (sortField.trim()) {
      case "hostname", "displayName", "osType", "primaryIp", "agentVersion", "status", "lastSeenAt", "createdAt" ->
          sortField.trim();
      default -> "lastSeenAt";
    };
  }
}
