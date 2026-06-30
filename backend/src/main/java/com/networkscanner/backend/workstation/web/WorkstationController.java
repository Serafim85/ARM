package com.networkscanner.backend.workstation.web;

import com.networkscanner.backend.workstation.api.WorkstationPort;
import com.networkscanner.backend.workstation.dto.WorkstationDetailDto;
import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationFilter;
import com.networkscanner.backend.workstation.dto.WorkstationListItemDto;
import com.networkscanner.backend.workstation.dto.WorkstationLogEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationMetricsHistoryDto;
import com.networkscanner.backend.workstation.dto.WorkstationPageDto;
import com.networkscanner.backend.workstation.report.ArmWorkstationParkReportService;
import com.networkscanner.backend.workstation.report.ArmWorkstationParkReportXlsxWriter;
import com.networkscanner.backend.workstation.report.WorkstationParkReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/workstations")
@Tag(name = "Workstations", description = "Реестр АРМ (WISLA ARM)")
public class WorkstationController {

  private final WorkstationPort workstationPort;
  private final ArmWorkstationParkReportService parkReportService;
  private final ArmWorkstationParkReportXlsxWriter parkReportXlsxWriter;

  public WorkstationController(
      WorkstationPort workstationPort,
      ArmWorkstationParkReportService parkReportService,
      ArmWorkstationParkReportXlsxWriter parkReportXlsxWriter
  ) {
    this.workstationPort = workstationPort;
    this.parkReportService = parkReportService;
    this.parkReportXlsxWriter = parkReportXlsxWriter;
  }

  @GetMapping
  @Operation(summary = "Список АРМ", description = "Пагинированный реестр рабочих станций с фильтрами.")
  public WorkstationPageDto list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String osType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "lastSeenAt") String sortField,
      @RequestParam(defaultValue = "desc") String sortOrder
  ) {
    return workstationPort.list(new WorkstationFilter(q, status, osType), page, size, sortField, sortOrder);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Карточка АРМ", description = "Детали рабочей станции по id.")
  public WorkstationDetailDto getById(@PathVariable long id) {
    try {
      return workstationPort.getById(id);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
  }

  @GetMapping("/{id}/metrics")
  @Operation(summary = "История метрик АРМ", description = "CPU, RAM и диск из metric_values (ключи arm.*).")
  public WorkstationMetricsHistoryDto metricsHistory(
      @PathVariable long id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @RequestParam(required = false) Integer maxPoints
  ) {
    try {
      return workstationPort.getMetricsHistory(id, from, to, maxPoints);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
  }

  @GetMapping("/{id}/logs")
  @Operation(summary = "Логи АРМ", description = "Warning/error записи из arm_log_events.")
  public List<WorkstationLogEntryDto> logs(
      @PathVariable long id,
      @RequestParam(required = false) String levels,
      @RequestParam(defaultValue = "50") int limit
  ) {
    try {
      List<String> parsedLevels = levels == null || levels.isBlank()
          ? List.of()
          : Arrays.stream(levels.split(",")).map(String::trim).toList();
      return workstationPort.getLogs(id, parsedLevels, limit);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
  }

  @GetMapping("/{id}/events")
  @Operation(summary = "События АРМ", description = "BSoD, kernel panic и другие значимые события.")
  public List<WorkstationEventEntryDto> events(
      @PathVariable long id,
      @RequestParam(defaultValue = "30") int limit
  ) {
    try {
      return workstationPort.getEvents(id, limit);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
  }

  @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
  @Operation(summary = "Экспорт реестра АРМ в CSV", description = "Выгрузка списка рабочих станций (v1 reports).")
  public ResponseEntity<String> exportCsv(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String osType
  ) {
    StringBuilder csv = new StringBuilder();
    csv.append("id,hostname,displayName,osType,primaryIp,agentVersion,status,lastSeenAt\n");
    int page = 0;
    while (true) {
      WorkstationPageDto pageDto = workstationPort.list(
          new WorkstationFilter(q, status, osType), page, 500, "hostname", "asc");
      for (WorkstationListItemDto row : pageDto.content()) {
        csv.append(row.id()).append(',')
            .append(csvCell(row.hostname())).append(',')
            .append(csvCell(row.displayName())).append(',')
            .append(csvCell(row.osType())).append(',')
            .append(csvCell(row.primaryIp())).append(',')
            .append(csvCell(row.agentVersion())).append(',')
            .append(csvCell(row.status())).append(',')
            .append(csvCell(row.lastSeenAt() == null ? "" : row.lastSeenAt().toString()))
            .append('\n');
      }
      if (pageDto.last()) {
        break;
      }
      page++;
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDispositionFormData("attachment", "workstations.csv");
    headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
    return new ResponseEntity<>(csv.toString(), headers, HttpStatus.OK);
  }

  @GetMapping(value = "/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  @Operation(
      summary = "Отчёт парка АРМ в XLSX",
      description = "Реестр с метриками и лист рекомендаций (v1 reports)."
  )
  public ResponseEntity<byte[]> exportXlsx(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String osType
  ) {
    WorkstationParkReport report = parkReportService.buildParkReport(new WorkstationFilter(q, status, osType));
    byte[] body = parkReportXlsxWriter.write(report);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDispositionFormData("attachment", "arm-park-report.xlsx");
    headers.setContentType(MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    return new ResponseEntity<>(body, headers, HttpStatus.OK);
  }

  private static String csvCell(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
