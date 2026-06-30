package com.networkscanner.backend.workstation.report;

import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationListItemDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ArmWorkstationRecommendationBuilder {

  private static final double DISK_WARN_PCT = 85.0;
  private static final double DISK_CRIT_PCT = 95.0;
  private static final double CPU_WARN_PCT = 80.0;

  private ArmWorkstationRecommendationBuilder() {
  }

  static WorkstationRecommendationRow build(
      WorkstationListItemDto workstation,
      Map<String, Double> latestMetrics,
      List<MonitoringEventEntity> openMonitoringEvents,
      List<WorkstationEventEntryDto> armEvents
  ) {
    List<String> items = new ArrayList<>();
    String priority = "INFO";

    if ("offline".equalsIgnoreCase(workstation.status())) {
      items.add("Проверить агент wisla-arm-agent и сетевую связь с сервером мониторинга.");
      priority = maxPriority(priority, "HIGH");
    }

    double diskPct = metric(latestMetrics, "arm.disk.root.used_pct");
    if (diskPct >= DISK_CRIT_PCT) {
      items.add("Критическая заполненность системного тома (≥95%): очистить диск или расширить том.");
      priority = maxPriority(priority, "HIGH");
    } else if (diskPct >= DISK_WARN_PCT) {
      items.add("Высокая заполненность системного тома (≥85%): запланировать очистку диска.");
      priority = maxPriority(priority, "WARNING");
    }

    double cpuPct = metric(latestMetrics, "arm.cpu.util");
    if (cpuPct >= CPU_WARN_PCT) {
      items.add("Высокая загрузка CPU (≥80%): проверить ресурсоёмкие процессы.");
      priority = maxPriority(priority, "WARNING");
    }

    for (MonitoringEventEntity event : openMonitoringEvents) {
      String trigger = event.getTriggerName() != null && !event.getTriggerName().isBlank()
          ? event.getTriggerName()
          : event.getMetricName();
      items.add("Открытый инцидент мониторинга: " + trigger + ".");
      priority = maxPriority(priority, thresholdPriority(event.getThresholdLevel()));
    }

    boolean bsod = armEvents != null && armEvents.stream()
        .anyMatch(entry -> entry != null && "BSOD".equalsIgnoreCase(entry.eventType()));
    if (bsod) {
      items.add("Зафиксировано событие BSoD: проверить стабильность ОС и журнал аварий.");
      priority = maxPriority(priority, "HIGH");
    }

    if (items.isEmpty()) {
      items.add("Без замечаний по текущим метрикам и открытым инцидентам.");
    }

    return new WorkstationRecommendationRow(
        workstation.hostname(),
        workstation.status(),
        priority,
        String.join(" ", items)
    );
  }

  static List<WorkstationRecommendationRow> sortByPriority(List<WorkstationRecommendationRow> rows) {
    return rows.stream()
        .sorted(Comparator.comparingInt(ArmWorkstationRecommendationBuilder::priorityRank).reversed()
            .thenComparing(row -> row.hostname() == null ? "" : row.hostname().toLowerCase(Locale.ROOT)))
        .toList();
  }

  private static int priorityRank(WorkstationRecommendationRow row) {
    return switch (row.priority() == null ? "INFO" : row.priority().toUpperCase(Locale.ROOT)) {
      case "HIGH", "DISASTER" -> 3;
      case "WARNING", "AVERAGE" -> 2;
      default -> 1;
    };
  }

  private static String maxPriority(String current, String candidate) {
    return priorityRank(new WorkstationRecommendationRow("", "", current, ""))
        >= priorityRank(new WorkstationRecommendationRow("", "", candidate, ""))
        ? current
        : candidate;
  }

  private static String thresholdPriority(ThresholdLevel level) {
    if (level == null) {
      return "WARNING";
    }
    return switch (level) {
      case DISASTER, HIGH -> "HIGH";
      case AVERAGE, WARNING -> "WARNING";
      default -> "INFO";
    };
  }

  private static double metric(Map<String, Double> latestMetrics, String key) {
    if (latestMetrics == null) {
      return 0.0;
    }
    Double value = latestMetrics.get(key);
    return value == null ? 0.0 : value;
  }
}
