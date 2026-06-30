package com.networkscanner.backend.network.scan.api;

import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.dto.ScanRunDto;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import java.util.List;
import java.util.Optional;

public interface ScanRunService {

  ScanRunStartResponse startManual(ScanRequest request);

  Optional<ScanRunStartResponse> startForJob(long jobId, boolean failIfRunning);

  ScanRunDto getStatus(long runId);

  List<DeviceScanResult> getResults(long runId);

  boolean stop(long runId);
}
