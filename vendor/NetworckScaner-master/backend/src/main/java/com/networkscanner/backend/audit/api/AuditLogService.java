package com.networkscanner.backend.audit.api;

import com.networkscanner.backend.audit.dto.AuditEventListQuery;
import com.networkscanner.backend.audit.dto.AuditEventPageDto;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface AuditLogService {

  void record(
      Authentication authentication,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  );

  /**
   * Запись от имени пользователя без объекта {@link Authentication} (например сразу после успешного логина).
   */
  void recordForActor(
      String actorLogin,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  );

  AuditEventPageDto list(Pageable pageable, AuditEventListQuery query);
}
