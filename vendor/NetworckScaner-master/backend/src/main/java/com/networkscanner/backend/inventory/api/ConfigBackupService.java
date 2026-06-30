package com.networkscanner.backend.inventory.api;

import com.networkscanner.backend.inventory.dto.BackupComparisonResult;
import com.networkscanner.backend.inventory.dto.DeviceBackupSnapshot;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.util.List;

public interface ConfigBackupService {

  void ensureBackupsForDevices(List<DeviceScanResult> devices);

  void removeDevices(List<String> ips);

  DeviceBackupSnapshot listBackups(String ip);

  DeviceBackupSnapshot setCurrentAsBaseline(DeviceScanResult device);

  DeviceBackupSnapshot uploadBaseline(String ip, String fileName, String content);

  DeviceBackupSnapshot setBackupAsBaseline(String ip, String backupId);

  BackupComparisonResult compareBackup(String ip, String backupId);

  String downloadBackup(String ip, String backupId);

  void migrateDeviceIp(String oldIp, String newIp);

  void runDailyAutoComparison();
}
