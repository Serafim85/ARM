package com.networkscanner.backend.agentingest.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networkscanner.backend.workstation.model.WorkstationEntity;
import org.junit.jupiter.api.Test;

class AgentIngestServiceImplTest {

  @Test
  void metricDeviceKey_prefersPrimaryIp() {
    WorkstationEntity ws = new WorkstationEntity();
    ws.setHostname("host1");
    ws.setPrimaryIp("10.0.0.5");
    assertEquals("10.0.0.5", AgentIngestServiceImpl.metricDeviceKey(ws));
  }

  @Test
  void metricDeviceKey_fallsBackToHostname() {
    WorkstationEntity ws = new WorkstationEntity();
    ws.setHostname("pilot-linux-01");
    assertEquals("pilot-linux-01", AgentIngestServiceImpl.metricDeviceKey(ws));
  }
}
