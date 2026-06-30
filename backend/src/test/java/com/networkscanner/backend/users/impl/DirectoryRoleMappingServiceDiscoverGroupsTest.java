package com.networkscanner.backend.users.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.users.dto.DirectoryGroupDto;
import com.networkscanner.backend.users.model.DirectorySettingsEntity;
import com.networkscanner.backend.users.repository.DirectoryRoleMappingRepository;
import com.networkscanner.backend.users.repository.DirectorySettingsRepository;
import java.util.List;
import java.util.Optional;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.junit.jupiter.api.Test;

class DirectoryRoleMappingServiceDiscoverGroupsTest {

  private final DirectoryRoleMappingRepository mappingRepository = mock(DirectoryRoleMappingRepository.class);
  private final DirectorySettingsRepository settingsRepository = mock(DirectorySettingsRepository.class);

  @Test
  void discoverGroups_returnsSortedGroupsFromDirectory() throws Exception {
    DirectorySettingsEntity settings = new DirectorySettingsEntity();
    settings.setId(1L);
    settings.setBaseDn("dc=networkscanner,dc=local");
    settings.setServerHost("localhost");
    settings.setServerPort(389);
    settings.setProtocol("LDAP");
    settings.setBindDn("cn=admin,dc=networkscanner,dc=local");
    settings.setBindPassword("admin");
    when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));

    DirContext context = mock(DirContext.class);
    NamingEnumeration<SearchResult> enumeration = mockNaming(
        group("cn=zeta,ou=groups,dc=networkscanner,dc=local", "Zeta"),
        group("cn=alpha,ou=groups,dc=networkscanner,dc=local", "Alpha")
    );
    when(context.search(eq("dc=networkscanner,dc=local"), any(String.class), any(SearchControls.class))).thenReturn(enumeration);

    DirectoryRoleMappingServiceImpl service = new TestableService(mappingRepository, settingsRepository, context);
    List<DirectoryGroupDto> groups = service.discoverGroups();

    assertThat(groups).hasSize(2);
    assertThat(groups.get(0).groupName()).isEqualTo("Alpha");
    assertThat(groups.get(1).groupName()).isEqualTo("Zeta");
  }

  @Test
  void discoverGroups_returnsEmptyOnNamingException() throws Exception {
    DirectorySettingsEntity settings = new DirectorySettingsEntity();
    settings.setId(1L);
    settings.setBaseDn("dc=networkscanner,dc=local");
    when(settingsRepository.findById(1L)).thenReturn(Optional.of(settings));

    DirectoryRoleMappingServiceImpl service =
        new DirectoryRoleMappingServiceImpl(mappingRepository, settingsRepository) {
          @Override
          protected DirContext createServiceContext(DirectorySettingsEntity ignored) throws NamingException {
            throw new NamingException("boom");
          }
        };

    assertThat(service.discoverGroups()).isEmpty();
  }

  @SafeVarargs
  @SuppressWarnings("unchecked")
  private static NamingEnumeration<SearchResult> mockNaming(SearchResult... entries) throws NamingException {
    NamingEnumeration<SearchResult> enumeration = mock(NamingEnumeration.class);
    if (entries.length == 0) {
      when(enumeration.hasMore()).thenReturn(false);
      return enumeration;
    }
    Boolean[] hasMore = new Boolean[entries.length + 1];
    for (int i = 0; i < entries.length; i++) {
      hasMore[i] = true;
    }
    hasMore[entries.length] = false;
    when(enumeration.hasMore()).thenReturn(hasMore[0], java.util.Arrays.copyOfRange(hasMore, 1, hasMore.length));
    when(enumeration.next()).thenReturn(entries[0], java.util.Arrays.copyOfRange(entries, 1, entries.length));
    return enumeration;
  }

  private static SearchResult group(String dn, String cn) throws NamingException {
    SearchResult result = mock(SearchResult.class);
    Attributes attrs = mock(Attributes.class);
    Attribute cnAttr = mock(Attribute.class);
    when(cnAttr.get()).thenReturn(cn);
    when(attrs.get("cn")).thenReturn(cnAttr);
    when(result.getAttributes()).thenReturn(attrs);
    when(result.getNameInNamespace()).thenReturn(dn);
    return result;
  }

  private static class TestableService extends DirectoryRoleMappingServiceImpl {
    private final DirContext context;

    private TestableService(
        DirectoryRoleMappingRepository mappingRepository,
        DirectorySettingsRepository settingsRepository,
        DirContext context
    ) {
      super(mappingRepository, settingsRepository);
      this.context = context;
    }

    @Override
    protected DirContext createServiceContext(DirectorySettingsEntity settings) {
      return context;
    }
  }
}
