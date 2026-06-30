package com.networkscanner.backend.workstation.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArmWorkstationParkReportXlsxWriterTest {

  @Test
  void writesNonEmptyWorkbook() {
    WorkstationParkReport report = new WorkstationParkReport(
        OffsetDateTime.now(),
        List.of(new WorkstationParkReportRow(
            1L,
            "pilot-linux-01",
            "pilot-linux-01",
            "linux",
            "10.0.0.1",
            "0.1.0",
            "online",
            OffsetDateTime.now(),
            12.0,
            4_294_967_296.0,
            45.0
        )),
        List.of(new WorkstationRecommendationRow(
            "pilot-linux-01",
            "online",
            "INFO",
            "Без замечаний по текущим метрикам и открытым инцидентам."
        ))
    );

    byte[] bytes = new ArmWorkstationParkReportXlsxWriter().write(report);

    assertTrue(bytes.length > 1000);
    assertTrue(bytes[0] == 'P' && bytes[1] == 'K');
  }
}
