package com.networkscanner.backend.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-device monitoring state event for wiSLA NS integration (topic {@code wisla.monitor-state}).
 *
 * <p>Shape:
 * <ul>
 *   <li>{@code device.state=MONITOR_ON}: {@code device} includes name, ip, templateIds, defaultTemplateVersion.</li>
 *   <li>{@code device.state=MONITOR_OFF}: {@code device} contains only {@code state} (templates removed, row kept).</li>
 *   <li>{@code device.state=DELETED}: {@code device} contains only {@code state} (row removed from NS).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MonitorStateSnapshot(
    String schemaVersion,
    String eventId,
    String sourceSystem,
    Long externalDeviceId,
    MonitorStateSnapshotDevice device
) {
}
