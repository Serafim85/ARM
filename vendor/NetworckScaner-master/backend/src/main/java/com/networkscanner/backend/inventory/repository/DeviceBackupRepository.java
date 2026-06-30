package com.networkscanner.backend.inventory.repository;

import com.networkscanner.backend.inventory.model.DeviceBackupEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeviceBackupRepository extends JpaRepository<DeviceBackupEntity, String> {

  List<DeviceBackupEntity> findByDeviceIpOrderByCreatedAtDesc(String deviceIp);

  void deleteByDeviceIpIn(List<String> deviceIps);

  @Modifying
  @Query("UPDATE DeviceBackupEntity b SET b.deviceIp = :newIp WHERE b.deviceIp = :oldIp")
  void updateDeviceIp(String oldIp, String newIp);
}
