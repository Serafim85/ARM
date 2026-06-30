package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.DirectorySettingsService;
import com.networkscanner.backend.users.dto.DirectorySettingsDto;
import com.networkscanner.backend.users.dto.UpdateDirectorySettingsRequest;
import com.networkscanner.backend.users.model.DirectorySettingsEntity;
import com.networkscanner.backend.users.repository.DirectorySettingsRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectorySettingsServiceImpl implements DirectorySettingsService {

  private static final long SETTINGS_ID = 1L;

  private final DirectorySettingsRepository repository;

  public DirectorySettingsServiceImpl(DirectorySettingsRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public DirectorySettingsDto getSettings() {
    return toDto(getOrCreateSettings());
  }

  @Override
  @Transactional
  public DirectorySettingsDto updateSettings(UpdateDirectorySettingsRequest request) {
    DirectorySettingsEntity entity = getOrCreateSettings();
    entity.setEnabled(Boolean.TRUE.equals(request.enabled()));
    entity.setDirectoryType(normalize(request.directoryType(), "LDAP"));
    entity.setProtocol(normalize(request.protocol(), "LDAP"));
    entity.setServerHost(normalize(request.serverHost(), "localhost"));
    entity.setServerPort(request.serverPort() == null ? 389 : request.serverPort());
    entity.setBaseDn(normalize(request.baseDn(), ""));
    entity.setAuthType(normalize(request.authType(), "SIMPLE"));
    entity.setBindDn(normalizeNullable(request.bindDn()));
    if (Boolean.TRUE.equals(request.clearBindPassword())) {
      entity.setBindPassword(null);
    } else if (request.bindPassword() != null && !request.bindPassword().isBlank()) {
      entity.setBindPassword(request.bindPassword());
    }
    entity.setUserFilter(normalize(request.userFilter(), "(|(uid={login})(mail={login})(sAMAccountName={login}))"));
    entity.setLoginAttribute(normalize(request.loginAttribute(), "uid"));
    entity.setEmailAttribute(normalize(request.emailAttribute(), "mail"));
    entity.setDisplayNameAttribute(normalize(request.displayNameAttribute(), "cn"));
    entity.setAllowLocalFallback(Boolean.TRUE.equals(request.allowLocalFallback()));
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    DirectorySettingsEntity saved = repository.save(entity);
    return toDto(saved);
  }

  private DirectorySettingsEntity getOrCreateSettings() {
    return repository.findById(SETTINGS_ID).orElseGet(() -> repository.save(defaultSettings()));
  }

  private DirectorySettingsEntity defaultSettings() {
    DirectorySettingsEntity entity = new DirectorySettingsEntity();
    entity.setId(SETTINGS_ID);
    entity.setEnabled(false);
    entity.setDirectoryType("LDAP");
    entity.setProtocol("LDAP");
    entity.setServerHost("localhost");
    entity.setServerPort(389);
    entity.setBaseDn("");
    entity.setAuthType("SIMPLE");
    entity.setBindDn("");
    entity.setBindPassword(null);
    entity.setUserFilter("(|(uid={login})(mail={login})(sAMAccountName={login}))");
    entity.setLoginAttribute("uid");
    entity.setEmailAttribute("mail");
    entity.setDisplayNameAttribute("cn");
    entity.setAllowLocalFallback(true);
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return entity;
  }

  private DirectorySettingsDto toDto(DirectorySettingsEntity entity) {
    return new DirectorySettingsDto(
        entity.isEnabled(),
        entity.getDirectoryType(),
        entity.getProtocol(),
        entity.getServerHost(),
        entity.getServerPort(),
        entity.getBaseDn(),
        entity.getAuthType(),
        entity.getBindDn(),
        "",
        entity.getBindPassword() != null && !entity.getBindPassword().isBlank(),
        entity.getUserFilter(),
        entity.getLoginAttribute(),
        entity.getEmailAttribute(),
        entity.getDisplayNameAttribute(),
        entity.isAllowLocalFallback()
    );
  }

  private static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String normalizeNullable(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
