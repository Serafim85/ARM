package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.dto.MonitorState;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshotDevice;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds {@link MonitorStateSnapshot} payloads for the per-device wiSLA topic. */
@Component
public class MonitorStateSnapshotMapper {

  static final String SCHEMA_VERSION = "1.1";
  static final String DEFAULT_TEMPLATE_VERSION = "1";

  /** Build MONITOR_ON snapshot (nested device data per wiSLA contract). */
  public MonitorStateSnapshot forMonitorOn(
      MonitoredDeviceEntity entity,
      String sourceSystem,
      List<String> templateIds
  ) {
    String defaultTemplateVersion = (entity.getTemplateVersion() == null
        || entity.getTemplateVersion().isBlank())
        ? DEFAULT_TEMPLATE_VERSION
        : entity.getTemplateVersion();
    List<String> ids = templateIds == null ? List.of() : templateIds;
    return new MonitorStateSnapshot(
        SCHEMA_VERSION,
        UUID.randomUUID().toString(),
        sourceSystem,
        entity.getId(),
        new MonitorStateSnapshotDevice(
            MonitorState.MONITOR_ON,
            entity.getName(),
            entity.getIp(),
            ids,
            defaultTemplateVersion
        )
    );
  }

  /** Build MONITOR_OFF snapshot ({@code device} contains only state). */
  public MonitorStateSnapshot forMonitorOff(Long externalDeviceId, String sourceSystem) {
    return minimalStateSnapshot(externalDeviceId, sourceSystem, MonitorState.MONITOR_OFF);
  }

  /** Build DELETED snapshot when the device row is removed from NS ({@code device} contains only state). */
  public MonitorStateSnapshot forDeleted(Long externalDeviceId, String sourceSystem) {
    return minimalStateSnapshot(externalDeviceId, sourceSystem, MonitorState.DELETED);
  }

  private MonitorStateSnapshot minimalStateSnapshot(
      Long externalDeviceId,
      String sourceSystem,
      MonitorState state
  ) {
    return new MonitorStateSnapshot(
        SCHEMA_VERSION,
        UUID.randomUUID().toString(),
        sourceSystem,
        externalDeviceId,
        new MonitorStateSnapshotDevice(state, null, null, null, null)
    );
  }
}
