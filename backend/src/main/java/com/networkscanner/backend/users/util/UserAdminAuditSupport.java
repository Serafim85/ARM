package com.networkscanner.backend.users.util;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.audit.util.AuditTextFormat;
import com.networkscanner.backend.users.dto.UserManagementDto;
import org.springframework.security.core.Authentication;

/** Формирование записей аудита по операциям с учётными записями. */
public final class UserAdminAuditSupport {

  private UserAdminAuditSupport() {
  }

  public static void recordProfileUpdate(
      AuditLogService auditLogService,
      Authentication authentication,
      UserManagementDto before,
      UserManagementDto after
  ) {
    boolean emailChanged = !before.email().equalsIgnoreCase(after.email());
    boolean nameChanged = !before.displayName().equals(after.displayName());
    if (!emailChanged && !nameChanged) {
      return;
    }

    String target = after.email();
    String details;
    if (emailChanged && nameChanged) {
      details = AuditTextFormat.profileEmailAndNameChange(
          before.email(),
          after.email(),
          before.displayName(),
          after.displayName()
      );
    } else if (emailChanged) {
      details = AuditTextFormat.profileEmailChange(before.email(), after.email());
    } else {
      details = AuditTextFormat.profileDisplayNameChange(before.displayName(), after.displayName());
    }

    auditLogService.record(
        authentication,
        AuditCategory.USER_ADMIN,
        AuditAction.UPDATE,
        target,
        details
    );
  }

  public static void recordPasswordReset(
      AuditLogService auditLogService,
      Authentication authentication,
      UserManagementDto user
  ) {
    auditLogService.record(
        authentication,
        AuditCategory.USER_ADMIN,
        AuditAction.UPDATE,
        user.email(),
        "Пароль сброшен администратором."
    );
  }
}
