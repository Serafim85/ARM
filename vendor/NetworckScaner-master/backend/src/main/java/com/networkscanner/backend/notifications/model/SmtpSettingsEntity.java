package com.networkscanner.backend.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "smtp_settings")
public class SmtpSettingsEntity {

  @Id
  private Long id;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "server_host", nullable = false, length = 255)
  private String serverHost;

  @Column(name = "server_port", nullable = false)
  private Integer serverPort;

  @Column(nullable = false)
  private boolean auth;

  @Column(nullable = false)
  private boolean starttls;

  @Column(nullable = false)
  private boolean ssl;

  @Column(length = 255)
  private String username;

  @Column(length = 512)
  private String password;

  @Column(name = "from_email", nullable = false, length = 255)
  private String fromEmail;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getServerHost() { return serverHost; }
  public void setServerHost(String serverHost) { this.serverHost = serverHost; }
  public Integer getServerPort() { return serverPort; }
  public void setServerPort(Integer serverPort) { this.serverPort = serverPort; }
  public boolean isAuth() { return auth; }
  public void setAuth(boolean auth) { this.auth = auth; }
  public boolean isStarttls() { return starttls; }
  public void setStarttls(boolean starttls) { this.starttls = starttls; }
  public boolean isSsl() { return ssl; }
  public void setSsl(boolean ssl) { this.ssl = ssl; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public String getFromEmail() { return fromEmail; }
  public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
