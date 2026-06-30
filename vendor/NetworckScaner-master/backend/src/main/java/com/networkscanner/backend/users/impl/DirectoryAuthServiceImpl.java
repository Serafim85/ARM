package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.users.api.DirectoryAuthService;
import com.networkscanner.backend.users.model.DirectorySettingsEntity;
import com.networkscanner.backend.users.repository.DirectorySettingsRepository;
import com.networkscanner.backend.audit.util.AuditTextFormat;
import com.networkscanner.backend.users.util.AuthAuditSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class DirectoryAuthServiceImpl implements DirectoryAuthService {

  private static final Logger log = LoggerFactory.getLogger(DirectoryAuthServiceImpl.class);

  private final DirectorySettingsRepository directorySettingsRepository;
  private final AuditLogService auditLogService;

  public DirectoryAuthServiceImpl(
      DirectorySettingsRepository directorySettingsRepository,
      AuditLogService auditLogService
  ) {
    this.directorySettingsRepository = directorySettingsRepository;
    this.auditLogService = auditLogService;
  }

  @Override
  public DirectoryAuthResult authenticate(String login, String password) {
    DirectorySettingsEntity settings = directorySettingsRepository.findById(1L).orElse(null);
    if (settings == null || !settings.isEnabled()) {
      return DirectoryAuthResult.disabled();
    }

    String authType = normalize(settings.getAuthType()).toUpperCase();
    if (!"SIMPLE".equals(authType)) {
      String reason = "Неподдерживаемый authType: " + settings.getAuthType();
      recordFailure(login, AuditAction.LOGIN_FAILED, reason);
      return new DirectoryAuthResult(
          DirectoryAuthStatus.UNSUPPORTED_AUTH_TYPE,
          null,
          null,
          null,
          List.of(),
          reason,
          settings.isAllowLocalFallback()
      );
    }

    if (password == null || password.isBlank()) {
      String reason = "Пустой пароль";
      recordFailure(login, AuditAction.LOGIN_FAILED, reason);
      return new DirectoryAuthResult(
          DirectoryAuthStatus.INVALID_CREDENTIALS,
          null,
          null,
          null,
          List.of(),
          reason,
          settings.isAllowLocalFallback()
      );
    }

    String userFilter = normalize(settings.getUserFilter())
        .replace("{login}", escapeFilter(login))
        .replace("{user}", escapeFilter(login));

    DirContext serviceContext = null;
    try {
      serviceContext = createServiceContext(settings);
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(new String[] {
          normalize(settings.getLoginAttribute()),
          normalize(settings.getEmailAttribute()),
          normalize(settings.getDisplayNameAttribute())
      });
      NamingEnumeration<SearchResult> result = serviceContext.search(settings.getBaseDn(), userFilter, controls);
      if (!result.hasMore()) {
        String reason = "Пользователь не найден по фильтру LDAP";
        recordFailure(login, AuditAction.LOGIN_FAILED, reason);
        return new DirectoryAuthResult(
            DirectoryAuthStatus.FILTER_REJECTED,
            null,
            null,
            null,
            List.of(),
            reason,
            settings.isAllowLocalFallback()
        );
      }
      SearchResult found = result.next();
      String userDn = found.getNameInNamespace();
      Attributes attrs = found.getAttributes();

      String resolvedLogin = firstAttr(attrs, settings.getLoginAttribute());
      String resolvedEmail = firstAttr(attrs, settings.getEmailAttribute());
      String resolvedDisplayName = firstAttr(attrs, settings.getDisplayNameAttribute());

      DirContext userContext = null;
      try {
        userContext = createUserContext(settings, userDn, password);
        String effectiveLogin = notBlankOr(resolvedEmail, notBlankOr(resolvedLogin, login));
        String displayName = notBlankOr(resolvedDisplayName, effectiveLogin);
        List<String> groupDns = resolveUserGroups(serviceContext, settings, userDn, resolvedLogin, login);
        recordSuccess(effectiveLogin, "Успешная LDAP/LDAPS аутентификация");
        return new DirectoryAuthResult(
            DirectoryAuthStatus.SUCCESS,
            effectiveLogin,
            notBlankOr(resolvedEmail, effectiveLogin),
            displayName,
            groupDns,
            null,
            settings.isAllowLocalFallback()
        );
      } catch (AuthenticationException ex) {
        String reason = "Неверные учетные данные каталога";
        recordFailure(login, AuditAction.LOGIN_FAILED, reason);
        return new DirectoryAuthResult(
            DirectoryAuthStatus.INVALID_CREDENTIALS,
            null,
            null,
            null,
            List.of(),
            reason,
            settings.isAllowLocalFallback()
        );
      } finally {
        closeQuietly(userContext);
      }
    } catch (CommunicationException | ServiceUnavailableException ex) {
      String reason = "Каталог недоступен: " + ex.getMessage();
      recordFailure(login, AuditAction.CONNECTION_ERROR, reason);
      return new DirectoryAuthResult(
          DirectoryAuthStatus.DIRECTORY_UNAVAILABLE,
          null,
          null,
          null,
          List.of(),
          reason,
          settings.isAllowLocalFallback()
      );
    } catch (NamingException ex) {
      String reason = "Ошибка LDAP: " + ex.getMessage();
      recordFailure(login, AuditAction.LOGIN_FAILED, reason);
      return new DirectoryAuthResult(
          DirectoryAuthStatus.INVALID_CREDENTIALS,
          null,
          null,
          null,
          List.of(),
          reason,
          settings.isAllowLocalFallback()
      );
    } finally {
      closeQuietly(serviceContext);
    }
  }

  private DirContext createServiceContext(DirectorySettingsEntity settings) throws NamingException {
    Hashtable<String, String> env = baseContext(settings);
    if (settings.getBindDn() != null && !settings.getBindDn().isBlank()) {
      env.put(javax.naming.Context.SECURITY_PRINCIPAL, settings.getBindDn());
      env.put(javax.naming.Context.SECURITY_CREDENTIALS, notBlankOr(settings.getBindPassword(), ""));
    }
    return new InitialDirContext(env);
  }

  private DirContext createUserContext(DirectorySettingsEntity settings, String userDn, String password) throws NamingException {
    Hashtable<String, String> env = baseContext(settings);
    env.put(javax.naming.Context.SECURITY_PRINCIPAL, userDn);
    env.put(javax.naming.Context.SECURITY_CREDENTIALS, password);
    return new InitialDirContext(env);
  }

  private Hashtable<String, String> baseContext(DirectorySettingsEntity settings) {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
    String protocol = normalize(settings.getProtocol()).equalsIgnoreCase("LDAPS") ? "ldaps" : "ldap";
    env.put(javax.naming.Context.PROVIDER_URL, protocol + "://" + settings.getServerHost() + ":" + settings.getServerPort());
    return env;
  }

  private void recordSuccess(String login, String reason) {
    String ip = resolveIp();
    log.info("directory-auth success login={} ip={} reason={}", login, ip, reason);
    auditLogService.recordForActor(
        login,
        AuditCategory.DIRECTORY_AUTH,
        AuditAction.LOGIN,
        login,
        "IP-адрес: " + AuditTextFormat.formatIp(ip) + ". " + AuditTextFormat.ensureSentence(reason)
    );
  }

  private void recordFailure(String login, AuditAction action, String reason) {
    String actor = notBlankOr(login, "unknown");
    String ip = resolveIp();
    log.warn("directory-auth failure login={} ip={} reason={}", actor, ip, reason);
    String details = AuditTextFormat.authFailureDetails(ip, reason);
    auditLogService.recordForActor(
        actor,
        AuditCategory.DIRECTORY_AUTH,
        action,
        actor,
        details
    );
    AuthAuditSupport.duplicateDirectoryFailureInAuthSession(auditLogService, actor, action, reason);
  }

  private String resolveIp() {
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return "unknown";
    }
    HttpServletRequest request = attrs.getRequest();
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
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

  private List<String> resolveUserGroups(
      DirContext serviceContext,
      DirectorySettingsEntity settings,
      String userDn,
      String resolvedLogin,
      String requestedLogin
  ) throws NamingException {
    List<String> groups = new ArrayList<>();
    String groupFilter = "(|(member=" + escapeFilter(userDn) + ")(uniqueMember=" + escapeFilter(userDn) + ")"
        + "(memberUid=" + escapeFilter(notBlankOr(resolvedLogin, requestedLogin)) + "))";
    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    controls.setReturningAttributes(new String[] {"distinguishedName", "dn", "cn"});
    NamingEnumeration<SearchResult> result = serviceContext.search(settings.getBaseDn(), groupFilter, controls);
    while (result.hasMore()) {
      SearchResult entry = result.next();
      String dn = entry.getNameInNamespace();
      if (dn != null && !dn.isBlank()) {
        groups.add(dn.trim());
      }
    }
    return groups;
  }

  private static String escapeFilter(String value) {
    return normalize(value)
        .replace("\\", "\\5c")
        .replace("*", "\\2a")
        .replace("(", "\\28")
        .replace(")", "\\29")
        .replace("\u0000", "\\00");
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static String notBlankOr(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  private static void closeQuietly(DirContext context) {
    if (context == null) {
      return;
    }
    try {
      context.close();
    } catch (NamingException ignore) {
      // intentionally ignored
    }
  }
}
