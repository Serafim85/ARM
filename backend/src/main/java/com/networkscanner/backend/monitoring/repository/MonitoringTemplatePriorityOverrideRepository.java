package com.networkscanner.backend.monitoring.repository;

import com.networkscanner.backend.monitoring.model.MonitoringTemplatePriorityOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringTemplatePriorityOverrideRepository
    extends JpaRepository<MonitoringTemplatePriorityOverrideEntity, String> {
}
