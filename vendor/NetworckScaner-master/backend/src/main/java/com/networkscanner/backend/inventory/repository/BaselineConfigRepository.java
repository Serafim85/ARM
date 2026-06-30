package com.networkscanner.backend.inventory.repository;

import com.networkscanner.backend.inventory.model.BaselineConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BaselineConfigRepository extends JpaRepository<BaselineConfigEntity, Long> {

  Optional<BaselineConfigEntity> findByDeviceIp(String deviceIp);

  void deleteByDeviceIpIn(Iterable<String> deviceIps);

  @Modifying
  @Query("UPDATE BaselineConfigEntity b SET b.deviceIp = :newIp WHERE b.deviceIp = :oldIp")
  void updateDeviceIp(String oldIp, String newIp);
}
