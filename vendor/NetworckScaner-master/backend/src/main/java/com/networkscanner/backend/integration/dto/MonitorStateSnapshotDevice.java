package com.networkscanner.backend.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Device block embedded in {@link MonitorStateSnapshot}.
 *
 * <p>For {@link MonitorState#MONITOR_ON}, {@code templateIds} uses the same tokens as storage
 * ({@code id} or {@code id:version} per entry). For {@link MonitorState#MONITOR_OFF}, only
 * {@code state} is serialized (other fields null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MonitorStateSnapshotDevice(
    MonitorState state,
    String name,
    String ipAddress,
    List<String> templateIds,
    String defaultTemplateVersion
) {
}
