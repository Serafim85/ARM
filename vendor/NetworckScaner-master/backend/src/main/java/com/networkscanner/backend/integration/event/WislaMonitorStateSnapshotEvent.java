package com.networkscanner.backend.integration.event;

import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;

/** Internal application event carrying a per-device monitor-state snapshot for AFTER_COMMIT publishing. */
public record WislaMonitorStateSnapshotEvent(MonitorStateSnapshot payload) {
}
