package com.networkscanner.backend.users.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.DirectorySettingsService;
import com.networkscanner.backend.users.dto.DirectorySettingsDto;
import com.networkscanner.backend.users.dto.UpdateDirectorySettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/directory-settings")
@Tag(name = "Системные настройки каталога", description = "Настройка LDAP/LDAPS/AD для внешней аутентификации (только ADMIN)")
public class AdminDirectorySettingsController {

  private final DirectorySettingsService directorySettingsService;
  private final AuditLogService auditLogService;

  public AdminDirectorySettingsController(
      DirectorySettingsService directorySettingsService,
      AuditLogService auditLogService
  ) {
    this.directorySettingsService = directorySettingsService;
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "Текущие настройки каталога")
  public DirectorySettingsDto getSettings() {
    return directorySettingsService.getSettings();
  }

  @PutMapping
  @Operation(summary = "Сохранить настройки каталога")
  public DirectorySettingsDto updateSettings(
      @Valid @RequestBody UpdateDirectorySettingsRequest request,
      Authentication authentication
  ) {
    DirectorySettingsDto settings = directorySettingsService.updateSettings(request);
    auditLogService.record(
        authentication,
        AuditCategory.DIRECTORY_CONFIG,
        AuditAction.UPDATE,
        "directory-settings",
        "enabled=" + settings.enabled()
            + "; host=" + settings.serverHost()
            + ":" + settings.serverPort()
            + "; type=" + settings.directoryType()
            + "; allowLocalFallback=" + settings.allowLocalFallback()
    );
    return settings;
  }
}
