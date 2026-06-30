package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.DirectoryRoleMappingService;
import com.networkscanner.backend.users.dto.DirectoryGroupDto;
import com.networkscanner.backend.users.dto.DirectoryRoleMappingDto;
import com.networkscanner.backend.users.dto.UpdateDirectoryRoleMappingsRequest;
import com.networkscanner.backend.users.model.DirectoryRoleMappingEntity;
import com.networkscanner.backend.users.model.DirectorySettingsEntity;
import com.networkscanner.backend.users.model.RoleName;
import com.networkscanner.backend.users.repository.DirectoryRoleMappingRepository;
import com.networkscanner.backend.users.repository.DirectorySettingsRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectoryRoleMappingServiceImpl implements DirectoryRoleMappingService {

  private final DirectoryRoleMappingRepository repository;
  private final DirectorySettingsRepository settingsRepository;

  public DirectoryRoleMappingServiceImpl(
      DirectoryRoleMappingRepository repository,
      DirectorySettingsRepository settingsRepository
  ) {
    this.repository = repository;
    this.settingsRepository = settingsRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DirectoryRoleMappingDto> listMappings() {
    return repository.findAllByOrderByGroupNameAsc().stream()
        .map(e -> new DirectoryRoleMappingDto(e.getGroupDn(), e.getGroupName(), e.getRoleName()))
        .toList();
  }

  @Override
  @Transactional
  public List<DirectoryRoleMappingDto> updateMappings(UpdateDirectoryRoleMappingsRequest request) {
    repository.deleteAllInBatch();
    List<DirectoryRoleMappingEntity> entities = new ArrayList<>();
    for (DirectoryRoleMappingDto item : request.items()) {
      if (item == null || item.groupDn() == null || item.groupDn().isBlank() || item.role() == null || item.role().isBlank()) {
        continue;
      }
      DirectoryRoleMappingEntity entity = new DirectoryRoleMappingEntity();
      entity.setGroupDn(item.groupDn().trim());
      entity.setGroupName(item.groupName() == null || item.groupName().isBlank() ? item.groupDn().trim() : item.groupName().trim());
      entity.setRoleName(item.role().trim().toUpperCase());
      entities.add(entity);
    }
    repository.saveAll(entities);
    return listMappings();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DirectoryGroupDto> discoverGroups() {
    DirectorySettingsEntity settings = settingsRepository.findById(1L).orElse(null);
    if (settings == null) {
      return List.of();
    }
    DirContext context = null;
    try {
      context = createServiceContext(settings);
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(new String[] {"cn"});
      NamingEnumeration<SearchResult> result = context.search(
          settings.getBaseDn(),
          "(|(objectClass=group)(objectClass=groupOfNames)(objectClass=groupOfUniqueNames))",
          controls
      );
      List<DirectoryGroupDto> groups = new ArrayList<>();
      while (result.hasMore()) {
        SearchResult entry = result.next();
        String dn = entry.getNameInNamespace();
        String name = readCn(entry.getAttributes());
        groups.add(new DirectoryGroupDto(dn, name == null || name.isBlank() ? dn : name));
      }
      return groups.stream()
          .sorted((a, b) -> a.groupName().compareToIgnoreCase(b.groupName()))
          .toList();
    } catch (NamingException ignored) {
      return List.of();
    } finally {
      closeQuietly(context);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Set<RoleName> resolveRolesForGroups(List<String> groupDns) {
    if (groupDns == null || groupDns.isEmpty()) {
      return Set.of();
    }
    List<String> normalized = groupDns.stream()
        .filter(v -> v != null && !v.isBlank())
        .map(v -> v.trim().toLowerCase())
        .toList();
    EnumSet<RoleName> roles = EnumSet.noneOf(RoleName.class);
    for (DirectoryRoleMappingEntity entity : repository.findAll()) {
      if (entity.getGroupDn() == null || entity.getRoleName() == null) {
        continue;
      }
      if (!normalized.contains(entity.getGroupDn().trim().toLowerCase())) {
        continue;
      }
      try {
        roles.add(RoleName.valueOf(entity.getRoleName().trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
        // skip invalid role values
      }
    }
    return roles;
  }

  protected DirContext createServiceContext(DirectorySettingsEntity settings) throws NamingException {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
    String protocol = "LDAPS".equalsIgnoreCase(settings.getProtocol()) ? "ldaps" : "ldap";
    env.put(javax.naming.Context.PROVIDER_URL, protocol + "://" + settings.getServerHost() + ":" + settings.getServerPort());
    if (settings.getBindDn() != null && !settings.getBindDn().isBlank()) {
      env.put(javax.naming.Context.SECURITY_PRINCIPAL, settings.getBindDn());
      env.put(javax.naming.Context.SECURITY_CREDENTIALS, settings.getBindPassword() == null ? "" : settings.getBindPassword());
    }
    return new InitialDirContext(env);
  }

  private static String readCn(Attributes attrs) throws NamingException {
    if (attrs == null) {
      return null;
    }
    Attribute cn = attrs.get("cn");
    if (cn == null || cn.get() == null) {
      return null;
    }
    return String.valueOf(cn.get()).trim();
  }

  private static void closeQuietly(DirContext context) {
    if (context == null) {
      return;
    }
    try {
      context.close();
    } catch (NamingException ignore) {
      // ignore
    }
  }
}
