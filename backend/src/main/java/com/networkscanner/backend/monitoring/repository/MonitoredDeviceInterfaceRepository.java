package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.MonitoredDeviceInterfaceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoredDeviceInterfaceRepository extends JpaRepository<MonitoredDeviceInterfaceEntity, Long> {

  List<MonitoredDeviceInterfaceEntity> findByDevice_IdOrderByNameAsc(Long deviceId);
}
