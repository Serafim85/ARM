package com.networkscanner.backend.inventory.impl;

import com.networkscanner.backend.inventory.api.ConfigBackupService;
import com.networkscanner.backend.inventory.dto.BackupComparisonResult;
import com.networkscanner.backend.inventory.dto.BaselineConfigSummary;
import com.networkscanner.backend.inventory.dto.DeviceBackupEntry;
import com.networkscanner.backend.inventory.dto.DeviceBackupSnapshot;
import com.networkscanner.backend.inventory.model.BaselineConfigEntity;
import com.networkscanner.backend.inventory.model.DeviceBackupEntity;
import com.networkscanner.backend.inventory.repository.BaselineConfigRepository;
import com.networkscanner.backend.inventory.repository.DeviceBackupRepository;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigBackupServiceImpl implements ConfigBackupService {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final String AUTO_COMPARE_SCHEDULE = "Каждый день в 02:00";

  private final DeviceBackupRepository deviceBackupRepository;
  private final BaselineConfigRepository baselineConfigRepository;
  private final JdbcTemplate jdbcTemplate;

  public ConfigBackupServiceImpl(
      DeviceBackupRepository deviceBackupRepository,
      BaselineConfigRepository baselineConfigRepository,
      JdbcTemplate jdbcTemplate
  ) {
    this.deviceBackupRepository = deviceBackupRepository;
    this.baselineConfigRepository = baselineConfigRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public synchronized void ensureBackupsForDevices(List<DeviceScanResult> devices) {
    for (DeviceScanResult device : devices) {
      if (!deviceBackupRepository.findByDeviceIpOrderByCreatedAtDesc(device.ip()).isEmpty()) {
        continue;
      }

      String content = generateConfigContent(device, "running-config", 0);
      DeviceBackupEntity initialBackup = createBackupEntity(
          device.ip(),
          "running-config",
          "Автосъем при постановке на мониторинг",
          content,
          "Успешно",
          "Эталон не задан",
          "Эталонный конфиг еще не задан.",
          null
      );
      deviceBackupRepository.save(initialBackup);
    }
  }

  @Override
  public synchronized void removeDevices(List<String> ips) {
    deviceBackupRepository.deleteByDeviceIpIn(ips);
    baselineConfigRepository.deleteByDeviceIpIn(ips);
  }

  @Override
  public synchronized DeviceBackupSnapshot listBackups(String ip) {
    return toSnapshot(ip, null);
  }

  @Override
  public synchronized DeviceBackupSnapshot setCurrentAsBaseline(DeviceScanResult device) {
    List<DeviceBackupEntity> backups = deviceBackupRepository.findByDeviceIpOrderByCreatedAtDesc(device.ip());
    String backupName = "current-config-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
    String content = generateConfigContent(device, backupName, backups.size());
    DeviceBackupEntity backup = createBackupEntity(
        device.ip(),
        backupName,
        "Текущая конфигурация",
        content,
        "Успешно",
        "Эталон не задан",
        "Эталонный конфиг еще не задан.",
        null
    );
    deviceBackupRepository.save(backup);
    saveBaseline(device.ip(), backup.getName() + ".cfg", "Сформирован из текущего состояния", backup.getContent());
    return compareAllBackups(device.ip(), null);
  }

  @Override
  public synchronized DeviceBackupSnapshot uploadBaseline(String ip, String fileName, String content) {
    saveBaseline(ip, fileName, "Загружен пользователем", content);
    return compareAllBackups(ip, null);
  }

  @Override
  public synchronized DeviceBackupSnapshot setBackupAsBaseline(String ip, String backupId) {
    DeviceBackupEntity backup = findBackup(ip, backupId);
    saveBaseline(ip, backup.getName() + ".cfg", "Выбран из существующего бэкапа", backup.getContent());
    return compareAllBackups(ip, null);
  }

  @Override
  public synchronized BackupComparisonResult compareBackup(String ip, String backupId) {
    BaselineConfigEntity baseline = getBaseline(ip);
    DeviceBackupEntity backup = findBackup(ip, backupId);
    ComparisonOutcome outcome = compareContents(backup.getContent(), baseline.getContent());
    backup.setBaselineStatus(outcome.status());
    backup.setComparisonSummary(outcome.summary());
    backup.setComparedAt(OffsetDateTime.now());
    deviceBackupRepository.save(backup);

    return new BackupComparisonResult(
        backup.getId(),
        backup.getName(),
        backup.getBaselineStatus(),
        formatDateTime(backup.getComparedAt()),
        backup.getComparisonSummary()
    );
  }

  @Override
  public synchronized String downloadBackup(String ip, String backupId) {
    return findBackup(ip, backupId).getContent();
  }

  @Override
  @Transactional
  public synchronized void migrateDeviceIp(String oldIp, String newIp) {
    deviceBackupRepository.updateDeviceIp(oldIp, newIp);
    baselineConfigRepository.updateDeviceIp(oldIp, newIp);
    jdbcTemplate.update("UPDATE metric_values SET device_ip = ? WHERE device_ip = ?", newIp, oldIp);
    jdbcTemplate.update("UPDATE availability_history SET device_ip = ? WHERE device_ip = ?", newIp, oldIp);
    jdbcTemplate.update("UPDATE telemetry_history SET device_ip = ? WHERE device_ip = ?", newIp, oldIp);
  }

  @Override
  @Scheduled(cron = "0 0 2 * * *")
  public synchronized void runDailyAutoComparison() {
    List<String> deviceIps = deviceBackupRepository.findAll().stream()
        .map(DeviceBackupEntity::getDeviceIp)
        .distinct()
        .toList();

    for (String ip : deviceIps) {
      List<DeviceBackupEntity> existingBackups = deviceBackupRepository.findByDeviceIpOrderByCreatedAtDesc(ip);
      String content = generateScheduledConfig(ip, existingBackups.size());
      DeviceBackupEntity nightlyBackup = createBackupEntity(
          ip,
          "nightly-config",
          "Автосравнение 02:00",
          content,
          "Успешно",
          "Эталон не задан",
          "Эталонный конфиг еще не задан.",
          null
      );
      deviceBackupRepository.save(nightlyBackup);
      compareAllBackups(ip, OffsetDateTime.now());
    }
  }

  private DeviceBackupSnapshot compareAllBackups(String ip, OffsetDateTime autoComparedAt) {
    BaselineConfigEntity baseline = baselineConfigRepository.findByDeviceIp(ip).orElse(null);
    List<DeviceBackupEntity> backups = deviceBackupRepository.findByDeviceIpOrderByCreatedAtDesc(ip);
    List<DeviceBackupEntity> updated = new ArrayList<>();
    OffsetDateTime comparisonTime = OffsetDateTime.now();

    for (DeviceBackupEntity backup : backups) {
      if (baseline == null) {
        backup.setBaselineStatus("Эталон не задан");
        backup.setComparisonSummary("Эталонная конфигурация еще не загружена.");
      } else {
        ComparisonOutcome outcome = compareContents(backup.getContent(), baseline.getContent());
        backup.setBaselineStatus(outcome.status());
        backup.setComparisonSummary(outcome.summary());
        backup.setComparedAt(comparisonTime);
      }
      updated.add(backup);
    }

    deviceBackupRepository.saveAll(updated);
    return toSnapshot(ip, autoComparedAt);
  }

  private DeviceBackupSnapshot toSnapshot(String ip, OffsetDateTime autoComparedAt) {
    BaselineConfigEntity baseline = baselineConfigRepository.findByDeviceIp(ip).orElse(null);
    List<DeviceBackupEntry> backups = deviceBackupRepository.findByDeviceIpOrderByCreatedAtDesc(ip).stream()
        .map(backup -> new DeviceBackupEntry(
            backup.getId(),
            backup.getName(),
            formatDateTime(backup.getCreatedAt()),
            backup.getSource(),
            backup.getSize(),
            backup.getStatus(),
            backup.getBaselineStatus(),
            backup.getComparisonSummary(),
            formatDateTimeNullable(backup.getComparedAt())
        ))
        .toList();

    BaselineConfigSummary baselineSummary = baseline == null
        ? null
        : new BaselineConfigSummary(
            baseline.getFileName(),
            formatDateTime(baseline.getConfiguredAt()),
            baseline.getSource()
        );

    return new DeviceBackupSnapshot(
        ip,
        AUTO_COMPARE_SCHEDULE,
        formatDateTimeNullable(autoComparedAt),
        baselineSummary,
        backups
    );
  }

  private void saveBaseline(String ip, String fileName, String source, String content) {
    BaselineConfigEntity baseline = baselineConfigRepository.findByDeviceIp(ip)
        .orElseGet(BaselineConfigEntity::new);
    baseline.setDeviceIp(ip);
    baseline.setFileName(fileName);
    baseline.setConfiguredAt(OffsetDateTime.now());
    baseline.setSource(source);
    baseline.setContent(content);
    baselineConfigRepository.save(baseline);
  }

  private BaselineConfigEntity getBaseline(String ip) {
    return baselineConfigRepository.findByDeviceIp(ip)
        .orElseThrow(() -> new IllegalArgumentException("Сначала задайте эталонную конфигурацию."));
  }

  private DeviceBackupEntity findBackup(String ip, String backupId) {
    DeviceBackupEntity backup = deviceBackupRepository.findById(backupId)
        .orElseThrow(() -> new IllegalArgumentException("Бэкап не найден."));
    if (!Objects.equals(backup.getDeviceIp(), ip)) {
      throw new IllegalArgumentException("Бэкап не принадлежит выбранному устройству.");
    }
    return backup;
  }

  private ComparisonOutcome compareContents(String backupContent, String baselineContent) {
    if (Objects.equals(backupContent, baselineContent)) {
      return new ComparisonOutcome("Совпадает", "Отличий от эталонной конфигурации не найдено.");
    }

    int diffLines = countDifferentLines(backupContent, baselineContent);
    return new ComparisonOutcome(
        "Есть отличия",
        "Найдены отличия относительно эталона. Измененных строк: " + diffLines + "."
    );
  }

  private int countDifferentLines(String left, String right) {
    String[] leftLines = left.split("\\R");
    String[] rightLines = right.split("\\R");
    int maxLength = Math.max(leftLines.length, rightLines.length);
    int differences = 0;
    for (int index = 0; index < maxLength; index++) {
      String leftValue = index < leftLines.length ? leftLines[index] : "";
      String rightValue = index < rightLines.length ? rightLines[index] : "";
      if (!Objects.equals(leftValue, rightValue)) {
        differences++;
      }
    }
    return differences;
  }

  private DeviceBackupEntity createBackupEntity(
      String deviceIp,
      String name,
      String source,
      String content,
      String status,
      String baselineStatus,
      String comparisonSummary,
      OffsetDateTime comparedAt
  ) {
    DeviceBackupEntity entity = new DeviceBackupEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setDeviceIp(deviceIp);
    entity.setName(name);
    entity.setCreatedAt(OffsetDateTime.now());
    entity.setSource(source);
    entity.setSize(formatSize(content));
    entity.setStatus(status);
    entity.setBaselineStatus(baselineStatus);
    entity.setComparisonSummary(comparisonSummary);
    entity.setComparedAt(comparedAt);
    entity.setContent(content);
    return entity;
  }

  private String generateConfigContent(DeviceScanResult device, String backupName, int version) {
    int seed = device.ip().chars().sum() + version;
    return """
        ! device=%s
        ! ip=%s
        ! backup=%s
        hostname %s
        snmp-server community public RO
        interface Vlan10
         description Users-VLAN
         ip address 192.168.%d.%d 255.255.255.0
         no shutdown
        interface Loopback0
         ip address 10.%d.%d.%d 255.255.255.255
        line vty 0 4
         login local
         transport input ssh
        """.formatted(
        device.hostName(),
        device.ip(),
        backupName,
        normalizeHostName(device.hostName(), device.ip()),
        150 + (seed % 40),
        10 + (seed % 120),
        1 + (seed % 9),
        10 + (seed % 20),
        20 + (seed % 50)
    );
  }

  private String generateScheduledConfig(String ip, int version) {
    int[] octets = parseOctets(ip);
    int dayModifier = LocalDate.now().getDayOfMonth() % 3;
    return """
        ! device=%s
        ! scheduled=02:00
        hostname NODE-%d-%d
        snmp-server community public RO
        interface Vlan10
         description Users-VLAN
         ip address 192.168.%d.%d 255.255.255.0
         no shutdown
        interface GigabitEthernet1/0/1
         description Uplink-%d
         switchport mode trunk
        line vty 0 4
         login local
         transport input ssh
        """.formatted(
        ip,
        octets[2],
        octets[3],
        octets[2],
        octets[3],
        version + dayModifier
    );
  }

  private int[] parseOctets(String ip) {
    String[] values = ip.split("\\.");
    int[] octets = new int[4];
    for (int index = 0; index < Math.min(values.length, 4); index++) {
      octets[index] = Integer.parseInt(values[index]);
    }
    return octets;
  }

  private String normalizeHostName(String hostName, String ip) {
    if (hostName != null && !hostName.isBlank() && !"-".equals(hostName)) {
      return hostName.replace(' ', '-');
    }
    return "NODE-" + ip.replace('.', '-');
  }

  private String formatSize(String content) {
    int size = Math.max(1, content.getBytes().length / 1024);
    return size + " KB";
  }

  private String formatDateTime(OffsetDateTime value) {
    return value.format(TIMESTAMP_FORMAT);
  }

  private String formatDateTimeNullable(OffsetDateTime value) {
    return value == null ? null : formatDateTime(value);
  }

  private record ComparisonOutcome(
      String status,
      String summary
  ) {
  }
}
