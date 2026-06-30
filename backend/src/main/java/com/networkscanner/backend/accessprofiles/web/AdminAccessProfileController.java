package com.networkscanner.backend.accessprofiles.web;

import com.networkscanner.backend.accessprofiles.api.AccessProfileService;
import com.networkscanner.backend.accessprofiles.dto.AccessProfileDetailDto;
import com.networkscanner.backend.accessprofiles.dto.UpsertAccessProfileRequest;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access-profiles")
@Tag(name = "Профили доступа (ADMIN)", description = "Управление профилями доступа для сканирования")
public class AdminAccessProfileController {

  private final AccessProfileService service;
  private final AuditLogService auditLogService;

  public AdminAccessProfileController(AccessProfileService service, AuditLogService auditLogService) {
    this.service = service;
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "Список профилей с деталями")
  public List<AccessProfileDetailDto> list() {
    return service.listDetails();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Профиль по идентификатору")
  public AccessProfileDetailDto get(@PathVariable Long id) {
    return service.getById(id);
  }

  @PostMapping
  @Operation(summary = "Создать профиль")
  public AccessProfileDetailDto create(
      @Valid @RequestBody UpsertAccessProfileRequest request,
      Authentication authentication
  ) {
    AccessProfileDetailDto created = service.create(request);
    auditLogService.record(
        authentication,
        AuditCategory.ACCESS_PROFILE,
        AuditAction.CREATE,
        String.valueOf(created.id()),
        "name=" + created.name()
    );
    return created;
  }

  @PutMapping("/{id}")
  @Operation(summary = "Обновить профиль")
  public AccessProfileDetailDto update(
      @PathVariable Long id,
      @Valid @RequestBody UpsertAccessProfileRequest request,
      Authentication authentication
  ) {
    AccessProfileDetailDto updated = service.update(id, request);
    auditLogService.record(
        authentication,
        AuditCategory.ACCESS_PROFILE,
        AuditAction.UPDATE,
        String.valueOf(updated.id()),
        "name=" + updated.name()
    );
    return updated;
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Удалить профиль")
  public void delete(@PathVariable Long id, Authentication authentication) {
    AccessProfileDetailDto existing = service.getById(id);
    service.delete(id);
    auditLogService.record(
        authentication,
        AuditCategory.ACCESS_PROFILE,
        AuditAction.DELETE,
        String.valueOf(existing.id()),
        "name=" + existing.name()
    );
  }
}
