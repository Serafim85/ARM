package com.networkscanner.backend.workstation.repository;

import com.networkscanner.backend.workstation.model.WorkstationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkstationRepository extends JpaRepository<WorkstationEntity, Long>, JpaSpecificationExecutor<WorkstationEntity> {

  Optional<WorkstationEntity> findByHostnameIgnoreCase(String hostname);
}
