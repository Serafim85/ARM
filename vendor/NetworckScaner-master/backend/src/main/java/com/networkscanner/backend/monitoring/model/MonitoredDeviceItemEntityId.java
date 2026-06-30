package com.networkscanner.backend.monitoring.model;

import java.io.Serializable;
import java.util.Objects;

public class MonitoredDeviceItemEntityId implements Serializable {

  private Long deviceId;
  private String itemUuid;
  private String instanceKey;

  public MonitoredDeviceItemEntityId() {
  }

  public MonitoredDeviceItemEntityId(Long deviceId, String itemUuid, String instanceKey) {
    this.deviceId = deviceId;
    this.itemUuid = itemUuid;
    this.instanceKey = instanceKey;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MonitoredDeviceItemEntityId that)) {
      return false;
    }
    return Objects.equals(deviceId, that.deviceId)
        && Objects.equals(itemUuid, that.itemUuid)
        && Objects.equals(instanceKey, that.instanceKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(deviceId, itemUuid, instanceKey);
  }
}
