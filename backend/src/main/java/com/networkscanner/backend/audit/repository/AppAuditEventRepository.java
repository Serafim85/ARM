package com.networkscanner.backend.audit.repository;

import com.networkscanner.backend.audit.model.AppAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppAuditEventRepository extends JpaRepository<AppAuditEventEntity, Long>,
    JpaSpecificationExecutor<AppAuditEventEntity> {
}
