package com.networkscanner.backend.integration.api;

import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.integration.dto.WislaBootstrapRequest;
import org.springframework.data.domain.Page;

public interface WislaBootstrapService {

  Page<ProbeBootstrapPayload> listMonitoredDevices(WislaBootstrapRequest request);
}
