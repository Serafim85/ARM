package com.networkscanner.backend.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "directory_settings")
public class DirectorySettingsEntity {

  @Id
  private Long id;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "directory_type", nullable = false, length = 32)
  private String directoryType;

  @Column(name = "protocol", nullable = false, length = 16)
  private String protocol;

  @Column(name = "server_host", nullable = false, length = 255)
  private String serverHost;

  @Column(name = "server_port", nullable = false)
  private Integer serverPort;

  @Column(name = "base_dn", nullable = false, length = 512)
  private String baseDn;

  @Column(name = "auth_type", nullable = false, length = 32)
  private String authType;

  @Column(name = "bind_dn", length = 512)
  private String bindDn;

  @Column(name = "bind_password", length = 512)
  private String bindPassword;

  @Column(name = "user_filter", nullable = false, length = 1024)
  private String userFilter;

  @Column(name = "login_attribute", nullable = false, length = 128)
  private String loginAttribute;

  @Column(name = "email_attribute", nullable = false, length = 128)
  private String emailAttribute;

  @Column(name = "display_name_attribute", nullable = false, length = 128)
  private String displayNameAttribute;

  @Column(name = "allow_local_fallback", nullable = false)
  private boolean allowLocalFallback;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getDirectoryType() {
    return directoryType;
  }

  public void setDirectoryType(String directoryType) {
    this.directoryType = directoryType;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public String getServerHost() {
    return serverHost;
  }

  public void setServerHost(String serverHost) {
    this.serverHost = serverHost;
  }

  public Integer getServerPort() {
    return serverPort;
  }

  public void setServerPort(Integer serverPort) {
    this.serverPort = serverPort;
  }

  public String getBaseDn() {
    return baseDn;
  }

  public void setBaseDn(String baseDn) {
    this.baseDn = baseDn;
  }

  public String getAuthType() {
    return authType;
  }

  public void setAuthType(String authType) {
    this.authType = authType;
  }

  public String getBindDn() {
    return bindDn;
  }

  public void setBindDn(String bindDn) {
    this.bindDn = bindDn;
  }

  public String getBindPassword() {
    return bindPassword;
  }

  public void setBindPassword(String bindPassword) {
    this.bindPassword = bindPassword;
  }

  public String getUserFilter() {
    return userFilter;
  }

  public void setUserFilter(String userFilter) {
    this.userFilter = userFilter;
  }

  public String getLoginAttribute() {
    return loginAttribute;
  }

  public void setLoginAttribute(String loginAttribute) {
    this.loginAttribute = loginAttribute;
  }

  public String getEmailAttribute() {
    return emailAttribute;
  }

  public void setEmailAttribute(String emailAttribute) {
    this.emailAttribute = emailAttribute;
  }

  public String getDisplayNameAttribute() {
    return displayNameAttribute;
  }

  public void setDisplayNameAttribute(String displayNameAttribute) {
    this.displayNameAttribute = displayNameAttribute;
  }

  public boolean isAllowLocalFallback() {
    return allowLocalFallback;
  }

  public void setAllowLocalFallback(boolean allowLocalFallback) {
    this.allowLocalFallback = allowLocalFallback;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
