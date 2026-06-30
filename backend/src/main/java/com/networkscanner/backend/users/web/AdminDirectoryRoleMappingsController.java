package com.networkscanner.backend.users.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.DirectoryRoleMappingService;
import com.networkscanner.backend.users.dto.DirectoryGroupDto;
import com.networkscanner.backend.users.dto.DirectoryRoleMappingDto;
import com.networkscanner.backend.users.dto.UpdateDirectoryRoleMappingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/directory-role-mappings")
@Tag(name = "Соответствие групп каталога ролям", description = "Назначение ролей системы по группам AD/LDAP (только ADMIN)")
public class AdminDirectoryRoleMappingsController {

  private final DirectoryRoleMappingService roleMappingService;
  private final AuditLogService auditLogService;

  public AdminDirectoryRoleMappingsController(
      DirectoryRoleMappingService roleMappingService,
      AuditLogService auditLogService
  ) {
    this.roleMappingService = roleMappingService;
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "Текущие соответствия групп и ролей")
  public List<DirectoryRoleMappingDto> listMappings() {
    return roleMappingService.listMappings();
  }

  @PutMapping
  @Operation(summary = "Сохранить соответствия групп и ролей")
  public List<DirectoryRoleMappingDto> updateMappings(
      @Valid @RequestBody UpdateDirectoryRoleMappingsRequest request,
      Authentication authentication
  ) {
    List<DirectoryRoleMappingDto> mappings = roleMappingService.updateMappings(request);
    auditLogService.record(
        authentication,
        AuditCategory.DIRECTORY_CONFIG,
        AuditAction.UPDATE,
        "directory-role-mappings",
        "Сохранено правил сопоставления: " + mappings.size()
    );
    return mappings;
  }

  @GetMapping("/discover-groups")
  @Operation(summary = "Получить список групп из каталога")
  public List<DirectoryGroupDto> discoverGroups() {
    return roleMappingService.discoverGroups();
  }
}
