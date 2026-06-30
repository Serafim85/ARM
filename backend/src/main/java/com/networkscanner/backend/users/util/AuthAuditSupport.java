package com.networkscanner.backend.users.util;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.audit.util.AuditTextFormat;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Запись событий аутентификации в журнал аудита (в т.ч. дубликат в AUTH_SESSION). */
public final class AuthAuditSupport {

  private AuthAuditSupport() {
  }

  public static String resolveClientIp() {
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return "unknown";
    }
    HttpServletRequest request = attrs.getRequest();
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  public static void recordAuthSessionFailure(
      AuditLogService auditLogService,
      String login,
      String reason
  ) {
    recordAuthSessionFailure(auditLogService, login, AuditAction.LOGIN_FAILED, reason);
  }

  public static void recordAuthSessionFailure(
      AuditLogService auditLogService,
      String login,
      AuditAction action,
      String reason
  ) {
    String actor = login != null && !login.isBlank() ? login : "unknown";
    String details = AuditTextFormat.authFailureDetails(resolveClientIp(), reason);
    auditLogService.recordForActor(
        actor,
        AuditCategory.AUTH_SESSION,
        action,
        actor,
        details
    );
  }

  /** Дублирует событие каталога в раздел «Сеанс» (неуспешный вход / ошибка подключения). */
  public static void duplicateDirectoryFailureInAuthSession(
      AuditLogService auditLogService,
      String login,
      AuditAction action,
      String reason
  ) {
    String actor = login != null && !login.isBlank() ? login : "unknown";
    auditLogService.recordForActor(
        actor,
        AuditCategory.AUTH_SESSION,
        action,
        actor,
        AuditTextFormat.authFailureDetails(resolveClientIp(), reason)
    );
  }
}
