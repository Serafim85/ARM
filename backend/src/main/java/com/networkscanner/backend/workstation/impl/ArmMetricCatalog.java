package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.workstation.dto.WorkstationMetricPointDto;
import com.networkscanner.backend.workstation.dto.WorkstationMetricSeriesDto;
import java.util.List;

final class ArmMetricCatalog {

  record Definition(String key, String displayName, String unit) {
  }

  static final List<Definition> ARM_METRICS = List.of(
      new Definition("arm.cpu.util", "CPU", "%"),
      new Definition("arm.mem.used", "Память", "B"),
      new Definition("arm.disk.root.used_pct", "Диск (корень)", "%")
  );

  private ArmMetricCatalog() {
  }
}
