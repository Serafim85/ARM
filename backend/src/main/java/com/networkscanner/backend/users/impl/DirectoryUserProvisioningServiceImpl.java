package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.DirectoryUserProvisioningService;
import com.networkscanner.backend.users.api.UserManagementService;
import com.networkscanner.backend.users.dto.CreateUserFromDirectoryRequest;
import com.networkscanner.backend.users.dto.DirectoryUserCandidateDto;
import com.networkscanner.backend.users.dto.DirectoryUserSearchRequest;
import com.networkscanner.backend.users.dto.UserManagementDto;
import com.networkscanner.backend.users.model.DirectorySettingsEntity;
import com.networkscanner.backend.users.model.RoleName;
import com.networkscanner.backend.users.repository.DirectorySettingsRepository;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.springframework.stereotype.Service;

@Service
public class DirectoryUserProvisioningServiceImpl implements DirectoryUserProvisioningService {

  private final DirectorySettingsRepository settingsRepository;
  private final UserManagementService userManagementService;

  public DirectoryUserProvisioningServiceImpl(
      DirectorySettingsRepository settingsRepository,
      UserManagementService userManagementService
  ) {
    this.settingsRepository = settingsRepository;
    this.userManagementService = userManagementService;
  }

  @Override
  public List<DirectoryUserCandidateDto> searchUsers(DirectoryUserSearchRequest request) {
    DirectorySettingsEntity settings = settingsRepository.findById(1L)
        .orElseThrow(() -> new IllegalArgumentException("Настройки LDAP не найдены."));
    DirContext context = null;
    try {
      context = createServiceContext(settings);
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String loginAttr = nonBlankOr(settings.getLoginAttribute(), "uid");
      String emailAttr = nonBlankOr(request.emailAttribute(), settings.getEmailAttribute());
      String displayNameAttr = nonBlankOr(request.displayNameAttribute(), settings.getDisplayNameAttribute());
      controls.setReturningAttributes(new String[] {loginAttr, emailAttr, displayNameAttr});
      NamingEnumeration<SearchResult> result = context.search(settings.getBaseDn(), request.ldapFilter().trim(), controls);
      java.util.ArrayList<DirectoryUserCandidateDto> users = new java.util.ArrayList<>();
      while (result.hasMore()) {
        SearchResult row = result.next();
        Attributes attrs = row.getAttributes();
        String login = firstAttr(attrs, loginAttr);
        String email = firstAttr(attrs, emailAttr);
        String display = firstAttr(attrs, displayNameAttr);
        if (email == null || email.isBlank()) {
          continue;
        }
        users.add(new DirectoryUserCandidateDto(
            row.getNameInNamespace(),
            login == null || login.isBlank() ? email : login,
            email,
            display == null || display.isBlank() ? email : display,
            resolveUserGroups(context, settings, row.getNameInNamespace(), login == null ? email : login)
        ));
      }
      return users;
    } catch (NamingException ex) {
      throw new IllegalArgumentException("Ошибка LDAP: " + ex.getMessage());
    } finally {
      closeQuietly(context);
    }
  }

  @Override
  public UserManagementDto createUserFromDirectory(CreateUserFromDirectoryRequest request) {
    RoleName role = RoleName.valueOf(request.role().trim().toUpperCase());
    String randomPassword = UUID.randomUUID().toString().replace("-", "") + "Aa1!";
    return userManagementService.createUser(
        request.email().trim().toLowerCase(),
        request.displayName().trim(),
        randomPassword,
        List.of(role),
        request.enabled()
    );
  }

  private DirContext createServiceContext(DirectorySettingsEntity settings) throws NamingException {
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

  private static String firstAttr(Attributes attrs, String attrName) throws NamingException {
    if (attrs == null || attrName == null || attrName.isBlank()) {
      return null;
    }
    javax.naming.directory.Attribute attr = attrs.get(attrName);
    if (attr == null || attr.get() == null) {
      return null;
    }
    return String.valueOf(attr.get()).trim();
  }

  private static String nonBlankOr(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private List<String> resolveUserGroups(
      DirContext context,
      DirectorySettingsEntity settings,
      String userDn,
      String login
  ) throws NamingException {
    String groupFilter = "(|(member=" + escapeFilter(userDn) + ")(uniqueMember=" + escapeFilter(userDn) + ")"
        + "(memberUid=" + escapeFilter(login) + "))";
    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    NamingEnumeration<SearchResult> result = context.search(settings.getBaseDn(), groupFilter, controls);
    java.util.ArrayList<String> groups = new java.util.ArrayList<>();
    while (result.hasMore()) {
      SearchResult row = result.next();
      String dn = row.getNameInNamespace();
      if (dn != null && !dn.isBlank()) {
        groups.add(dn.trim());
      }
    }
    return groups;
  }

  private static String escapeFilter(String value) {
    String v = value == null ? "" : value.trim();
    return v.replace("\\", "\\5c")
        .replace("*", "\\2a")
        .replace("(", "\\28")
        .replace(")", "\\29")
        .replace("\u0000", "\\00");
  }

  private static void closeQuietly(DirContext context) {
    if (context == null) return;
    try {
      context.close();
    } catch (NamingException ignore) {
      // ignore
    }
  }
}
