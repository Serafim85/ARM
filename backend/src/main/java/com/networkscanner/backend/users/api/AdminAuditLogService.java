package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.AdminAuditLogDto;
import java.util.List;
import org.springframework.security.core.Authentication;

/**
 * @deprecated Устаревший in-memory журнал (макс. 100 записей). Новые события пишутся в {@code AuditLogService}
 *     (таблица {@code app_audit_event}). API сохранён для обратной совместимости.
 */
@Deprecated
public interface AdminAuditLogService {

  List<AdminAuditLogDto> list();

  void record(Authentication authentication, String action, String target, String details);
}
