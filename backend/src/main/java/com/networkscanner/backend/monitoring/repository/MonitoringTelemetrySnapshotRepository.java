package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.MonitoringTelemetrySnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringTelemetrySnapshotRepository extends JpaRepository<MonitoringTelemetrySnapshotEntity, Long> {

  Optional<MonitoringTelemetrySnapshotEntity> findByDevice_Id(Long deviceId);
}
