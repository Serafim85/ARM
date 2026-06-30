package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.DirectoryAuthService;
import com.networkscanner.backend.users.api.DirectoryRoleMappingService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.users.api.AuthService;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.users.api.JwtService;
import com.networkscanner.backend.users.dto.LoginRequest;
import com.networkscanner.backend.users.dto.LoginResponse;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.model.RoleName;
import com.networkscanner.backend.users.repository.AppUserRepository;
import com.networkscanner.backend.users.util.AuthAuditSupport;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

  private final AppUserRepository userRepository;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;
  private final DashboardService dashboardService;
  private final TopologyService topologyService;
  private final AuditLogService auditLogService;
  private final DirectoryAuthService directoryAuthService;
  private final DirectoryRoleMappingService directoryRoleMappingService;

  public AuthServiceImpl(
      AppUserRepository userRepository,
      AuthenticationManager authenticationManager,
      JwtService jwtService,
      DashboardService dashboardService,
      TopologyService topologyService,
      AuditLogService auditLogService,
      DirectoryAuthService directoryAuthService,
      DirectoryRoleMappingService directoryRoleMappingService
  ) {
    this.userRepository = userRepository;
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
    this.dashboardService = dashboardService;
    this.topologyService = topologyService;
    this.auditLogService = auditLogService;
    this.directoryAuthService = directoryAuthService;
    this.directoryRoleMappingService = directoryRoleMappingService;
  }

  @Override
  public LoginResponse login(LoginRequest request) {
    AppUser user;
    try {
      user = authenticateAgainstDirectoryOrLocal(request);
    } catch (RuntimeException exception) {
      recordLoginFailure(request, exception);
      throw exception;
    }

    Long defaultDashboardId = normalizeDefaultDashboardId(user);
    Long defaultTopologyId = normalizeDefaultTopologyId(user);
    auditLogService.recordForActor(
        user.getEmail(),
        AuditCategory.AUTH_SESSION,
        AuditAction.LOGIN,
        user.getEmail(),
        "Вход в систему."
    );
    return new LoginResponse(
        "Успешный вход в систему.",
        jwtService.generateToken(user),
        user.getEmail(),
        user.getDisplayName(),
        user.getRoles().stream().map(Enum::name).toList(),
        user.getId(),
        defaultDashboardId,
        defaultTopologyId
    );
  }

  private AppUser authenticateAgainstDirectoryOrLocal(LoginRequest request) {
    String mode = normalizeMode(request.authMode());
    if ("LOCAL".equals(mode)) {
      return authenticateLocalOnly(request.email(), request.password());
    }
    if ("LDAP".equals(mode)) {
      return authenticateDirectoryOnly(request.email(), request.password());
    }

    DirectoryAuthService.DirectoryAuthResult directoryResult = directoryAuthService.authenticate(request.email(), request.password());
    if (directoryResult.status() == DirectoryAuthService.DirectoryAuthStatus.SUCCESS) {
      AppUser user = loadLocalUserFromDirectoryResult(directoryResult);
      applyMappedDirectoryRoles(user, directoryResult.groupDns());
      return user;
    }

    boolean canUseLocalFallback = directoryResult.status() == DirectoryAuthService.DirectoryAuthStatus.DISABLED
        || (directoryResult.status() == DirectoryAuthService.DirectoryAuthStatus.DIRECTORY_UNAVAILABLE
        && directoryResult.allowLocalFallback());
    if (!canUseLocalFallback) {
      throw new UsernameNotFoundException(directoryResult.failureReason() == null
          ? "Вход через каталог отклонен."
          : directoryResult.failureReason());
    }
    return authenticateLocalOnly(request.email(), request.password());
  }

  private AppUser authenticateDirectoryOnly(String login, String password) {
    DirectoryAuthService.DirectoryAuthResult directoryResult = directoryAuthService.authenticate(login, password);
    if (directoryResult.status() != DirectoryAuthService.DirectoryAuthStatus.SUCCESS) {
      throw new UsernameNotFoundException(directoryResult.failureReason() == null
          ? "Вход через каталог отклонен."
          : directoryResult.failureReason());
    }
    AppUser user = loadLocalUserFromDirectoryResult(directoryResult);
    applyMappedDirectoryRoles(user, directoryResult.groupDns());
    return user;
  }

  private AppUser loadLocalUserFromDirectoryResult(DirectoryAuthService.DirectoryAuthResult directoryResult) {
    String mappedLogin = directoryResult.email() != null ? directoryResult.email() : directoryResult.login();
    AppUser user = userRepository.findByEmailIgnoreCase(mappedLogin).orElse(null);
    if (user == null) {
      throw new UsernameNotFoundException("Пользователь не найден в локальной базе.");
    }
    if (!user.isEnabled()) {
      throw new UsernameNotFoundException("Пользователь заблокирован.");
    }
    return user;
  }

  private void recordLoginFailure(LoginRequest request, RuntimeException exception) {
    if (shouldSkipAuthSessionFailureAudit(request, exception)) {
      return;
    }
    String reason = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    AuthAuditSupport.recordAuthSessionFailure(auditLogService, request.email(), reason);
  }

  private static boolean shouldSkipAuthSessionFailureAudit(LoginRequest request, RuntimeException exception) {
    String mode = normalizeMode(request.authMode());
    String reason = exception.getMessage() != null ? exception.getMessage() : "";
    if (isPostDirectoryLocalRejection(reason)) {
      return false;
    }
    if ("LOCAL".equals(mode)) {
      return false;
    }
    if ("LDAP".equals(mode)) {
      return true;
    }
    return isDirectoryAuthFailureReason(reason);
  }

  private static boolean isPostDirectoryLocalRejection(String reason) {
    return reason.contains("не найден в локальной базе") || reason.contains("заблокирован");
  }

  private static boolean isDirectoryAuthFailureReason(String reason) {
    return reason.contains("каталог")
        || reason.contains("LDAP")
        || reason.contains("LDAPS")
        || reason.contains("authType")
        || reason.contains("Пустой пароль")
        || reason.contains("Вход через каталог отклонен");
  }

  private AppUser authenticateLocalOnly(String login, String password) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(login, password)
    );
    return userRepository.findByEmailIgnoreCase(login)
        .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден."));
  }

  private static String normalizeMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return "AUTO";
    }
    return mode.trim().toUpperCase();
  }

  private void applyMappedDirectoryRoles(AppUser user, java.util.List<String> groupDns) {
    java.util.Set<RoleName> mappedRoles = directoryRoleMappingService.resolveRolesForGroups(groupDns);
    if (mappedRoles.isEmpty()) {
      return;
    }
    user.setRoles(new java.util.LinkedHashSet<>(mappedRoles));
  }

  private Long normalizeDefaultDashboardId(AppUser user) {
    Long dashboardId = user.getDefaultDashboardId();
    if (dashboardId == null) {
      return null;
    }
    boolean admin = user.getRoles().contains(RoleName.ADMIN);
    if (dashboardService.isReadableByUser(dashboardId, user.getId(), admin)) {
      return dashboardId;
    }
    user.setDefaultDashboardId(null);
    userRepository.save(user);
    return null;
  }

  private Long normalizeDefaultTopologyId(AppUser user) {
    Long topologyId = user.getDefaultTopologyId();
    if (topologyId == null) {
      return null;
    }
    boolean admin = user.getRoles().contains(RoleName.ADMIN);
    if (topologyService.isReadableByUser(topologyId, user.getId(), admin)) {
      return topologyId;
    }
    user.setDefaultTopologyId(null);
    userRepository.save(user);
    return null;
  }
}
