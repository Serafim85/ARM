package com.networkscanner.backend.network.scan.repository;

import com.networkscanner.backend.network.scan.model.ScanRunEntity;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRunRepository extends JpaRepository<ScanRunEntity, Long> {

  Optional<ScanRunEntity> findFirstByScanJobIdAndStatusInOrderByIdDesc(
      long scanJobId,
      Collection<ScanRunStatus> statuses
  );

  List<ScanRunEntity> findByStatusIn(Collection<ScanRunStatus> statuses);
}
