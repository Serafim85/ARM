package com.networkscanner.backend.users.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.users.model.DirectoryRoleMappingEntity;
import com.networkscanner.backend.users.model.RoleName;
import com.networkscanner.backend.users.repository.DirectoryRoleMappingRepository;
import com.networkscanner.backend.users.repository.DirectorySettingsRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DirectoryRoleMappingServiceImplTest {

  private final DirectoryRoleMappingRepository mappingRepository = mock(DirectoryRoleMappingRepository.class);
  private final DirectorySettingsRepository settingsRepository = mock(DirectorySettingsRepository.class);

  private final DirectoryRoleMappingServiceImpl service =
      new DirectoryRoleMappingServiceImpl(mappingRepository, settingsRepository);

  @Test
  void resolveRolesForGroups_matchesConfiguredDnCaseInsensitive() {
    DirectoryRoleMappingEntity adminMap = new DirectoryRoleMappingEntity();
    adminMap.setGroupDn("cn=app-admins,ou=groups,dc=networkscanner,dc=local");
    adminMap.setGroupName("app-admins");
    adminMap.setRoleName("ADMIN");

    DirectoryRoleMappingEntity viewerMap = new DirectoryRoleMappingEntity();
    viewerMap.setGroupDn("cn=app-viewers,ou=groups,dc=networkscanner,dc=local");
    viewerMap.setGroupName("app-viewers");
    viewerMap.setRoleName("VIEWER");

    when(mappingRepository.findAll()).thenReturn(List.of(adminMap, viewerMap));

    Set<RoleName> roles = service.resolveRolesForGroups(List.of(
        "CN=APP-ADMINS,OU=GROUPS,DC=NETWORKSCANNER,DC=LOCAL",
        "cn=unknown,ou=groups,dc=networkscanner,dc=local"
    ));

    assertThat(roles).containsExactly(RoleName.ADMIN);
  }

  @Test
  void resolveRolesForGroups_skipsInvalidRoleValues() {
    DirectoryRoleMappingEntity invalidMap = new DirectoryRoleMappingEntity();
    invalidMap.setGroupDn("cn=app-broken,ou=groups,dc=networkscanner,dc=local");
    invalidMap.setGroupName("app-broken");
    invalidMap.setRoleName("NOT_A_ROLE");
    when(mappingRepository.findAll()).thenReturn(List.of(invalidMap));

    Set<RoleName> roles = service.resolveRolesForGroups(List.of("cn=app-broken,ou=groups,dc=networkscanner,dc=local"));

    assertThat(roles).isEmpty();
  }
}
