package com.networkscanner.backend.users.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.users.api.DirectoryAuthService;
import com.networkscanner.backend.users.api.DirectoryRoleMappingService;
import com.networkscanner.backend.users.api.JwtService;
import com.networkscanner.backend.users.dto.LoginRequest;
import com.networkscanner.backend.users.dto.LoginResponse;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.model.RoleName;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class AuthServiceImplTest {

  private final AppUserRepository userRepository = mock(AppUserRepository.class);
  private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
  private final JwtService jwtService = mock(JwtService.class);
  private final DashboardService dashboardService = mock(DashboardService.class);
  private final TopologyService topologyService = mock(TopologyService.class);
  private final AuditLogService auditLogService = mock(AuditLogService.class);
  private final DirectoryAuthService directoryAuthService = mock(DirectoryAuthService.class);
  private final DirectoryRoleMappingService directoryRoleMappingService = mock(DirectoryRoleMappingService.class);

  private final AuthServiceImpl service = new AuthServiceImpl(
      userRepository,
      authenticationManager,
      jwtService,
      dashboardService,
      topologyService,
      auditLogService,
      directoryAuthService,
      directoryRoleMappingService
  );

  @Test
  void login_ldapMode_appliesMappedRolesFromDirectoryGroups() {
    AppUser user = createUser("admin@example.com", Set.of(RoleName.VIEWER));
    when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
    when(jwtService.generateToken(any(AppUser.class))).thenReturn("token");
    when(directoryAuthService.authenticate("admin", "password")).thenReturn(
        new DirectoryAuthService.DirectoryAuthResult(
            DirectoryAuthService.DirectoryAuthStatus.SUCCESS,
            "admin",
            "admin@example.com",
            "Администратор системы",
            java.util.List.of("cn=app-admins,ou=groups,dc=networkscanner,dc=local"),
            null,
            true
        )
    );
    when(directoryRoleMappingService.resolveRolesForGroups(java.util.List.of("cn=app-admins,ou=groups,dc=networkscanner,dc=local")))
        .thenReturn(Set.of(RoleName.ADMIN, RoleName.OPERATOR));

    LoginResponse response = service.login(new LoginRequest("admin", "password", "LDAP"));

    assertThat(response.roles()).containsExactlyInAnyOrder("ADMIN", "OPERATOR");
  }

  @Test
  void login_ldapMode_keepsLocalRolesWhenNoMappingFound() {
    AppUser user = createUser("viewer@example.com", Set.of(RoleName.VIEWER));
    when(userRepository.findByEmailIgnoreCase("viewer@example.com")).thenReturn(Optional.of(user));
    when(jwtService.generateToken(any(AppUser.class))).thenReturn("token");
    when(directoryAuthService.authenticate("viewer", "viewer123")).thenReturn(
        new DirectoryAuthService.DirectoryAuthResult(
            DirectoryAuthService.DirectoryAuthStatus.SUCCESS,
            "viewer",
            "viewer@example.com",
            "Наблюдатель",
            java.util.List.of("cn=no-mapping,ou=groups,dc=networkscanner,dc=local"),
            null,
            true
        )
    );
    when(directoryRoleMappingService.resolveRolesForGroups(java.util.List.of("cn=no-mapping,ou=groups,dc=networkscanner,dc=local")))
        .thenReturn(Set.of());

    LoginResponse response = service.login(new LoginRequest("viewer", "viewer123", "LDAP"));

    assertThat(response.roles()).containsExactly("VIEWER");
  }

  @Test
  void login_localMode_badCredentials_recordsAuthSessionFailure() {
    when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

    assertThatThrownBy(() -> service.login(new LoginRequest("user@example.com", "wrong", "LOCAL")))
        .isInstanceOf(BadCredentialsException.class);

    verify(auditLogService).recordForActor(
        eq("user@example.com"),
        eq(AuditCategory.AUTH_SESSION),
        eq(AuditAction.LOGIN_FAILED),
        eq("user@example.com"),
        org.mockito.ArgumentMatchers.contains("Причина:")
    );
  }

  @Test
  void login_ldapMode_blockedLocalUser_recordsAuthSessionFailure() {
    AppUser user = createUser("blocked@example.com", Set.of(RoleName.VIEWER));
    user.setEnabled(false);
    when(directoryAuthService.authenticate("blocked", "password")).thenReturn(
        new DirectoryAuthService.DirectoryAuthResult(
            DirectoryAuthService.DirectoryAuthStatus.SUCCESS,
            "blocked",
            "blocked@example.com",
            "Blocked",
            java.util.List.of(),
            null,
            true
        )
    );
    when(userRepository.findByEmailIgnoreCase("blocked@example.com")).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(() -> service.login(new LoginRequest("blocked", "password", "LDAP")))
        .isInstanceOf(UsernameNotFoundException.class);

    verify(auditLogService).recordForActor(
        eq("blocked"),
        eq(AuditCategory.AUTH_SESSION),
        eq(AuditAction.LOGIN_FAILED),
        eq("blocked"),
        org.mockito.ArgumentMatchers.contains("заблокирован")
    );
  }

  private static AppUser createUser(String email, Set<RoleName> roles) {
    AppUser user = new AppUser();
    user.setEmail(email);
    user.setDisplayName(email);
    user.setPasswordHash("hash");
    user.setEnabled(true);
    user.setCreatedAt(OffsetDateTime.now());
    user.setRoles(new LinkedHashSet<>(roles));
    return user;
  }
}
