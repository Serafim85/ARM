package com.networkscanner.backend.users.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.AdminAuditLogService;
import com.networkscanner.backend.users.api.UserManagementService;
import com.networkscanner.backend.users.dto.AdminAuditLogDto;
import com.networkscanner.backend.users.dto.CreateUserRequest;
import com.networkscanner.backend.users.dto.ResetUserPasswordRequest;
import com.networkscanner.backend.users.dto.UpdateUserProfileRequest;
import com.networkscanner.backend.users.dto.UpdateUserRolesRequest;
import com.networkscanner.backend.users.dto.UpdateUserStatusRequest;
import com.networkscanner.backend.users.dto.UserManagementDto;
import com.networkscanner.backend.users.util.UserAdminAuditSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Администрирование пользователей", description = "Управление учётными записями и журнал аудита (только роль ADMIN)")
public class AdminUserController {

  private final UserManagementService userManagementService;
  private final AdminAuditLogService legacyAuditLogService;
  private final AuditLogService auditLogService;

  public AdminUserController(
      UserManagementService userManagementService,
      AdminAuditLogService legacyAuditLogService,
      AuditLogService auditLogService
  ) {
    this.userManagementService = userManagementService;
    this.legacyAuditLogService = legacyAuditLogService;
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "Список пользователей", description = "Возвращает всех пользователей системы с ролями и статусом.")
  public List<UserManagementDto> listUsers() {
    return userManagementService.listUsers();
  }

  @GetMapping("/audit-logs")
  @Operation(summary = "Журнал действий администраторов", description = "Устаревший in-memory журнал. Актуальные записи — GET /api/admin/audit/events.")
  public List<AdminAuditLogDto> listAuditLogs() {
    return legacyAuditLogService.list();
  }

  @PostMapping
  @Operation(
      summary = "Создать пользователя",
      description = "Создаёт учётную запись с указанными ролями и паролем. Событие фиксируется в аудите."
  )
  public UserManagementDto createUser(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
    UserManagementDto user = userManagementService.createUser(
        request.email(),
        request.displayName(),
        request.password(),
        request.roles(),
        request.enabled()
    );
    auditLogService.record(
        authentication,
        AuditCategory.USER_ADMIN,
        AuditAction.CREATE,
        user.email(),
        "Создан пользователь с ролями " + user.roles()
    );
    return user;
  }

  @PutMapping("/{userId}/profile")
  @Operation(
      summary = "Обновить профиль",
      description = "Меняет отображаемое имя и email пользователя."
  )
  public UserManagementDto updateProfile(
      @Parameter(description = "Идентификатор пользователя") @PathVariable Long userId,
      @Valid @RequestBody UpdateUserProfileRequest request,
      Authentication authentication
  ) {
    UserManagementDto before = userManagementService.getUserById(userId);
    UserManagementDto after = userManagementService.updateProfile(userId, request.email(), request.displayName());
    UserAdminAuditSupport.recordProfileUpdate(auditLogService, authentication, before, after);
    return after;
  }

  @PutMapping("/{userId}/roles")
  @Operation(
      summary = "Назначить роли",
      description = "Полная замена набора ролей пользователя. Ограничения по смене собственных ролей задаются в сервисе."
  )
  public UserManagementDto updateRoles(
      @Parameter(description = "Идентификатор пользователя") @PathVariable Long userId,
      @Valid @RequestBody UpdateUserRolesRequest request,
      Authentication authentication
  ) {
    UserManagementDto user = userManagementService.updateRoles(userId, request.roles(), authentication);
    auditLogService.record(
        authentication,
        AuditCategory.USER_ADMIN,
        AuditAction.UPDATE,
        user.email(),
        "Новые роли: " + user.roles()
    );
    return user;
  }

  @PutMapping("/{userId}/status")
  @Operation(
      summary = "Блокировка / разблокировка",
      description = "Включает или отключает вход пользователя (enabled)."
  )
  public UserManagementDto updateStatus(
      @Parameter(description = "Идентификатор пользователя") @PathVariable Long userId,
      @RequestBody UpdateUserStatusRequest request,
      Authentication authentication
  ) {
    UserManagementDto user = userManagementService.updateStatus(userId, request.enabled(), authentication);
    auditLogService.record(
        authentication,
        AuditCategory.USER_ADMIN,
        AuditAction.UPDATE,
        user.email(),
        request.enabled() ? "Учётная запись разблокирована." : "Учётная запись заблокирована."
    );
    return user;
  }

  @PutMapping("/{userId}/password")
  @Operation(
      summary = "Сброс пароля",
      description = "Устанавливает новый пароль пользователю. Тело ответа пустое при успехе."
  )
  public void resetPassword(
      @Parameter(description = "Идентификатор пользователя") @PathVariable Long userId,
      @Valid @RequestBody ResetUserPasswordRequest request,
      Authentication authentication
  ) {
    UserManagementDto user = userManagementService.resetPassword(userId, request.password());
    UserAdminAuditSupport.recordPasswordReset(auditLogService, authentication, user);
  }
}
