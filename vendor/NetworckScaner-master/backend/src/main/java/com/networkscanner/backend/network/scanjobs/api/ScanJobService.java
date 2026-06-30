package com.networkscanner.backend.network.scanjobs.api;

import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import com.networkscanner.backend.network.scanjobs.dto.DiscoveredNotMonitoredSummaryDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobDetailsDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobMetaUpdateRequest;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobUpsertRequest;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface ScanJobService {

  List<ScanJobDto> list();

  ScanJobDto get(long id);

  ScanJobDetailsDto getDetails(long id);

  ScanJobDto create(ScanJobUpsertRequest request, Authentication authentication);

  ScanJobDto update(long id, ScanJobUpsertRequest request, Authentication authentication);

  ScanJobDto updateMeta(long id, ScanJobMetaUpdateRequest request, Authentication authentication);

  ScanJobDto setEnabled(long id, boolean enabled, Authentication authentication);

  List<DeviceScanResult> getLastResult(long id);

  DiscoveredNotMonitoredSummaryDto getDiscoveredNotMonitoredSummary();

  List<DeviceScanResult> getDiscoveredNotMonitoredDevices();

  ScanRunStartResponse runNow(long id);

  void delete(long id, Authentication authentication);
}

