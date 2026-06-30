package com.networkscanner.backend.workstation.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationListItemDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArmWorkstationRecommendationBuilderTest {

  @Test
  void offlineWorkstationGetsHighPriorityRecommendation() {
    WorkstationListItemDto workstation = workstation("pilot-linux-01", "offline");

    WorkstationRecommendationRow row = ArmWorkstationRecommendationBuilder.build(
        workstation,
        Map.of(),
        List.of(),
        List.of()
    );

    assertEquals("HIGH", row.priority());
    assertTrue(row.recommendation().contains("агент"));
  }

  @Test
  void criticalDiskGetsCleanupRecommendation() {
    WorkstationListItemDto workstation = workstation("pilot-linux-01", "online");

    WorkstationRecommendationRow row = ArmWorkstationRecommendationBuilder.build(
        workstation,
        Map.of("arm.disk.root.used_pct", 96.0),
        List.of(),
        List.of()
    );

    assertEquals("HIGH", row.priority());
    assertTrue(row.recommendation().contains("95%"));
  }

  @Test
  void healthyWorkstationGetsNoIssuesText() {
    WorkstationListItemDto workstation = workstation("pilot-linux-01", "online");

    WorkstationRecommendationRow row = ArmWorkstationRecommendationBuilder.build(
        workstation,
        Map.of("arm.disk.root.used_pct", 40.0, "arm.cpu.util", 12.0),
        List.of(),
        List.of()
    );

    assertEquals("INFO", row.priority());
    assertTrue(row.recommendation().contains("Без замечаний"));
  }

  @Test
  void openMonitoringEventIsIncluded() {
    WorkstationListItemDto workstation = workstation("pilot-linux-01", "online");
    MonitoringEventEntity event = new MonitoringEventEntity();
    event.setTriggerName("ARM Linux: Root disk space critical");
    event.setMetricName("arm.disk.root.used_pct");
    event.setThresholdLevel(ThresholdLevel.HIGH);

    WorkstationRecommendationRow row = ArmWorkstationRecommendationBuilder.build(
        workstation,
        Map.of(),
        List.of(event),
        List.of()
    );

    assertEquals("HIGH", row.priority());
    assertTrue(row.recommendation().contains("Root disk space critical"));
  }

  @Test
  void bsodEventRaisesPriority() {
    WorkstationListItemDto workstation = workstation("pilot-windows-01", "online");
    WorkstationEventEntryDto bsod = new WorkstationEventEntryDto(
        1L,
        OffsetDateTime.now(),
        "BSOD",
        "HIGH",
        "Unexpected shutdown",
        "0xEF",
        "CRITICAL_PROCESS_DIED",
        "agent"
    );

    WorkstationRecommendationRow row = ArmWorkstationRecommendationBuilder.build(
        workstation,
        Map.of(),
        List.of(),
        List.of(bsod)
    );

    assertEquals("HIGH", row.priority());
    assertTrue(row.recommendation().contains("BSoD"));
  }

  private static WorkstationListItemDto workstation(String hostname, String status) {
    return new WorkstationListItemDto(
        1L,
        hostname,
        hostname,
        "linux",
        "10.0.0.1",
        "0.1.0",
        status,
        OffsetDateTime.now()
    );
  }
}
