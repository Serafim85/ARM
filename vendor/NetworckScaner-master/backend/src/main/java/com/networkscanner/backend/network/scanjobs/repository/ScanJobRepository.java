package com.networkscanner.backend.network.scanjobs.repository;

import com.networkscanner.backend.network.scanjobs.model.ScanJobEntity;
import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanJobRepository extends JpaRepository<ScanJobEntity, Long> {
  List<ScanJobEntity> findAllByEnabledTrue();

  List<ScanJobEntity> findByLastStatus(ScanJobStatus lastStatus);
}

