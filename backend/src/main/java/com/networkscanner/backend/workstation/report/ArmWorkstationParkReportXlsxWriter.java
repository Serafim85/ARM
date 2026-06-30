package com.networkscanner.backend.workstation.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ArmWorkstationParkReportXlsxWriter {

  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  public byte[] write(WorkstationParkReport report) {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      CellStyle headerStyle = headerStyle(workbook);
      writeRegistrySheet(workbook, headerStyle, report.registry());
      writeRecommendationsSheet(workbook, headerStyle, report.recommendations());
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Не удалось сформировать XLSX-отчёт", exception);
    }
  }

  private void writeRegistrySheet(Workbook workbook, CellStyle headerStyle, List<WorkstationParkReportRow> rows) {
    Sheet sheet = workbook.createSheet("Реестр АРМ");
    String[] headers = {
        "ID", "Hostname", "Имя", "ОС", "IP", "Версия агента", "Статус", "Last seen",
        "CPU %", "RAM (ГБ)", "Диск корень %"
    };
    writeHeader(sheet, headerStyle, headers);
    int rowIndex = 1;
    for (WorkstationParkReportRow row : rows) {
      Row excelRow = sheet.createRow(rowIndex++);
      int col = 0;
      setLong(excelRow, col++, row.id());
      setText(excelRow, col++, row.hostname());
      setText(excelRow, col++, row.displayName());
      setText(excelRow, col++, row.osType());
      setText(excelRow, col++, row.primaryIp());
      setText(excelRow, col++, row.agentVersion());
      setText(excelRow, col++, row.status());
      setText(excelRow, col++, formatDate(row.lastSeenAt()));
      setNumber(excelRow, col++, row.cpuUtilPct());
      setNumber(excelRow, col++, toGiB(row.memUsedBytes()));
      setNumber(excelRow, col, row.diskRootUsedPct());
    }
    autosize(sheet, headers.length);
  }

  private void writeRecommendationsSheet(
      Workbook workbook,
      CellStyle headerStyle,
      List<WorkstationRecommendationRow> rows
  ) {
    Sheet sheet = workbook.createSheet("Рекомендации");
    String[] headers = {"Hostname", "Статус", "Приоритет", "Рекомендация"};
    writeHeader(sheet, headerStyle, headers);
    int rowIndex = 1;
    for (WorkstationRecommendationRow row : rows) {
      Row excelRow = sheet.createRow(rowIndex++);
      setText(excelRow, 0, row.hostname());
      setText(excelRow, 1, row.status());
      setText(excelRow, 2, row.priority());
      setText(excelRow, 3, row.recommendation());
    }
    autosize(sheet, headers.length);
  }

  private static CellStyle headerStyle(Workbook workbook) {
    Font font = workbook.createFont();
    font.setBold(true);
    CellStyle style = workbook.createCellStyle();
    style.setFont(font);
    return style;
  }

  private static void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
    Row headerRow = sheet.createRow(0);
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }
  }

  private static void setText(Row row, int col, String value) {
    row.createCell(col).setCellValue(value == null ? "" : value);
  }

  private static void setLong(Row row, int col, Long value) {
    if (value == null) {
      row.createCell(col).setBlank();
      return;
    }
    row.createCell(col).setCellValue(value);
  }

  private static void setNumber(Row row, int col, Double value) {
    if (value == null) {
      row.createCell(col).setBlank();
      return;
    }
    row.createCell(col).setCellValue(value);
  }

  private static String formatDate(OffsetDateTime value) {
    return value == null ? "" : value.format(DATE_TIME);
  }

  private static Double toGiB(Double bytes) {
    if (bytes == null) {
      return null;
    }
    return bytes / (1024.0 * 1024.0 * 1024.0);
  }

  private static void autosize(Sheet sheet, int columns) {
    for (int i = 0; i < columns; i++) {
      sheet.autoSizeColumn(i);
    }
  }
}
