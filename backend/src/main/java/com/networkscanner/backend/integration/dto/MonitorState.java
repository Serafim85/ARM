package com.networkscanner.backend.integration.dto;

/** Device monitoring state inside {@link MonitorStateSnapshotDevice} (wiSLA NS integration). */
public enum MonitorState {
  MONITOR_ON,
  MONITOR_OFF,
  /** Physical removal of the device from NS inventory (wiSLA: archive probe/services, delete tickets). */
  DELETED
}
