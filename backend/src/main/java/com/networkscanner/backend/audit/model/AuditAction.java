package com.networkscanner.backend.audit.model;

public enum AuditAction {
  CREATE,
  UPDATE,
  DELETE,
  LOGIN,
  LOGOUT,
  LOGIN_FAILED,
  CONNECTION_ERROR,
  INTEGRATION_PUBLISH_FAILED
}
