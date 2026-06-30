package com.networkscanner.backend.users.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.DirectoryUserProvisioningService;
import com.networkscanner.backend.users.dto.CreateUserFromDirectoryRequest;
import com.networkscanner.backend.users.dto.DirectoryUserCandidateDto;
import com.networkscanner.backend.users.dto.DirectoryUserSearchRequest;
import com.networkscanner.backend.users.dto.UserManagementDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/directory-users")
@Tag(name = "Создание пользователей из каталога", description = "Поиск в LDAP/AD и создание локального пользователя")
public class AdminDirectoryUserProvisioningController {

  private final DirectoryUserProvisioningService directoryUserProvisioningService;
  private final AuditLogService auditLogService;

  public AdminDirectoryUserProvisioningController(
      DirectoryUserProvisioningService directoryUserProvisioningService,
      AuditLogService auditLogService
  ) {
    this.directoryUserProvisioningService = directoryUserProvisioningService;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/search")
  @Operation(summary = "Поиск пользователей в каталоге")
  public List<DirectoryUserCandidateDto> searchUsers(@Valid @RequestBody DirectoryUserSearchRequest request) {
    return directoryUserProvisioningService.searchUsers(request);
  }

  @PostMapping("/create")
  @Operation(summary = "Создать локального пользователя из каталога")
  public UserManagementDto createUser(
      @Valid @RequestBody CreateUserFromDirectoryRequest request,
      Authentication authentication
  ) {
    UserManagementDto user = directoryUserProvisioningService.createUserFromDirectory(request);
    auditLogService.record(
        authentication,
        AuditCategory.DIRECTORY_CONFIG,
        AuditAction.CREATE,
        user.email(),
        "Создан локальный пользователь из каталога LDAP/AD с ролями " + user.roles()
    );
    return user;
  }
}
