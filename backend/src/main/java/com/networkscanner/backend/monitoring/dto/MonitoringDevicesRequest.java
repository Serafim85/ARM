package com.networkscanner.backend.monitoring.dto;

import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record MonitoringDevicesRequest(
    @NotEmpty List<DeviceScanResult> devices,
    String templateId,
    List<String> templateIds,
    Map<String, String> perDeviceTemplateIds,
    Map<String, List<String>> perDeviceTemplateIdLists,
    MonitoringSnmpCredentials snmpCredentials,
    Long accessProfileIdForActivation
) {
}
