package com.networkscanner.backend.accessprofiles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "access_profiles")
public class AccessProfileEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 255)
  private String name;

  @Column(length = 1024)
  private String description;

  @Column(name = "snmp_v1_enabled", nullable = false)
  private boolean snmpV1Enabled;

  @Column(name = "snmp_v1_port")
  private Integer snmpV1Port;

  @Column(name = "snmp_v1_community", length = 512)
  private String snmpV1Community;

  @Column(name = "snmp_v2_enabled", nullable = false)
  private boolean snmpV2Enabled;

  @Column(name = "snmp_v2_port")
  private Integer snmpV2Port;

  @Column(name = "snmp_v2_community", length = 512)
  private String snmpV2Community;

  @Column(name = "snmp_v3_enabled", nullable = false)
  private boolean snmpV3Enabled;

  @Column(name = "snmp_v3_port")
  private Integer snmpV3Port;

  @Column(name = "snmp_v3_security_username", length = 255)
  private String snmpV3SecurityUsername;

  @Column(name = "snmp_v3_auth_protocol", length = 32)
  private String snmpV3AuthProtocol;

  @Column(name = "snmp_v3_auth_password", length = 512)
  private String snmpV3AuthPassword;

  @Column(name = "snmp_v3_privacy_protocol", length = 32)
  private String snmpV3PrivacyProtocol;

  @Column(name = "snmp_v3_privacy_password", length = 512)
  private String snmpV3PrivacyPassword;

  @Column(name = "ssh_enabled", nullable = false)
  private boolean sshEnabled;

  @Column(name = "ssh_port")
  private Integer sshPort;

  @Column(name = "ssh_username", length = 255)
  private String sshUsername;

  @Column(name = "ssh_password", length = 512)
  private String sshPassword;

  @Column(name = "ssh_private_key_pem", columnDefinition = "TEXT")
  private String sshPrivateKeyPem;

  @Column(name = "ssh_passphrase", length = 512)
  private String sshPassphrase;

  @Column(name = "https_enabled", nullable = false)
  private boolean httpsEnabled;

  @Column(name = "https_port")
  private Integer httpsPort;

  @Column(name = "https_username", length = 255)
  private String httpsUsername;

  @Column(name = "https_password", length = 512)
  private String httpsPassword;

  @Column(name = "https_client_cert_pem", columnDefinition = "TEXT")
  private String httpsClientCertPem;

  @Column(name = "https_client_key_pem", columnDefinition = "TEXT")
  private String httpsClientKeyPem;

  @Column(name = "https_insecure_skip_verify", nullable = false)
  private boolean httpsInsecureSkipVerify;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isSnmpV1Enabled() {
    return snmpV1Enabled;
  }

  public void setSnmpV1Enabled(boolean snmpV1Enabled) {
    this.snmpV1Enabled = snmpV1Enabled;
  }

  public Integer getSnmpV1Port() {
    return snmpV1Port;
  }

  public void setSnmpV1Port(Integer snmpV1Port) {
    this.snmpV1Port = snmpV1Port;
  }

  public String getSnmpV1Community() {
    return snmpV1Community;
  }

  public void setSnmpV1Community(String snmpV1Community) {
    this.snmpV1Community = snmpV1Community;
  }

  public boolean isSnmpV2Enabled() {
    return snmpV2Enabled;
  }

  public void setSnmpV2Enabled(boolean snmpV2Enabled) {
    this.snmpV2Enabled = snmpV2Enabled;
  }

  public Integer getSnmpV2Port() {
    return snmpV2Port;
  }

  public void setSnmpV2Port(Integer snmpV2Port) {
    this.snmpV2Port = snmpV2Port;
  }

  public String getSnmpV2Community() {
    return snmpV2Community;
  }

  public void setSnmpV2Community(String snmpV2Community) {
    this.snmpV2Community = snmpV2Community;
  }

  public boolean isSnmpV3Enabled() {
    return snmpV3Enabled;
  }

  public void setSnmpV3Enabled(boolean snmpV3Enabled) {
    this.snmpV3Enabled = snmpV3Enabled;
  }

  public Integer getSnmpV3Port() {
    return snmpV3Port;
  }

  public void setSnmpV3Port(Integer snmpV3Port) {
    this.snmpV3Port = snmpV3Port;
  }

  public String getSnmpV3SecurityUsername() {
    return snmpV3SecurityUsername;
  }

  public void setSnmpV3SecurityUsername(String snmpV3SecurityUsername) {
    this.snmpV3SecurityUsername = snmpV3SecurityUsername;
  }

  public String getSnmpV3AuthProtocol() {
    return snmpV3AuthProtocol;
  }

  public void setSnmpV3AuthProtocol(String snmpV3AuthProtocol) {
    this.snmpV3AuthProtocol = snmpV3AuthProtocol;
  }

  public String getSnmpV3AuthPassword() {
    return snmpV3AuthPassword;
  }

  public void setSnmpV3AuthPassword(String snmpV3AuthPassword) {
    this.snmpV3AuthPassword = snmpV3AuthPassword;
  }

  public String getSnmpV3PrivacyProtocol() {
    return snmpV3PrivacyProtocol;
  }

  public void setSnmpV3PrivacyProtocol(String snmpV3PrivacyProtocol) {
    this.snmpV3PrivacyProtocol = snmpV3PrivacyProtocol;
  }

  public String getSnmpV3PrivacyPassword() {
    return snmpV3PrivacyPassword;
  }

  public void setSnmpV3PrivacyPassword(String snmpV3PrivacyPassword) {
    this.snmpV3PrivacyPassword = snmpV3PrivacyPassword;
  }

  public boolean isSshEnabled() {
    return sshEnabled;
  }

  public void setSshEnabled(boolean sshEnabled) {
    this.sshEnabled = sshEnabled;
  }

  public Integer getSshPort() {
    return sshPort;
  }

  public void setSshPort(Integer sshPort) {
    this.sshPort = sshPort;
  }

  public String getSshUsername() {
    return sshUsername;
  }

  public void setSshUsername(String sshUsername) {
    this.sshUsername = sshUsername;
  }

  public String getSshPassword() {
    return sshPassword;
  }

  public void setSshPassword(String sshPassword) {
    this.sshPassword = sshPassword;
  }

  public String getSshPrivateKeyPem() {
    return sshPrivateKeyPem;
  }

  public void setSshPrivateKeyPem(String sshPrivateKeyPem) {
    this.sshPrivateKeyPem = sshPrivateKeyPem;
  }

  public String getSshPassphrase() {
    return sshPassphrase;
  }

  public void setSshPassphrase(String sshPassphrase) {
    this.sshPassphrase = sshPassphrase;
  }

  public boolean isHttpsEnabled() {
    return httpsEnabled;
  }

  public void setHttpsEnabled(boolean httpsEnabled) {
    this.httpsEnabled = httpsEnabled;
  }

  public Integer getHttpsPort() {
    return httpsPort;
  }

  public void setHttpsPort(Integer httpsPort) {
    this.httpsPort = httpsPort;
  }

  public String getHttpsUsername() {
    return httpsUsername;
  }

  public void setHttpsUsername(String httpsUsername) {
    this.httpsUsername = httpsUsername;
  }

  public String getHttpsPassword() {
    return httpsPassword;
  }

  public void setHttpsPassword(String httpsPassword) {
    this.httpsPassword = httpsPassword;
  }

  public String getHttpsClientCertPem() {
    return httpsClientCertPem;
  }

  public void setHttpsClientCertPem(String httpsClientCertPem) {
    this.httpsClientCertPem = httpsClientCertPem;
  }

  public String getHttpsClientKeyPem() {
    return httpsClientKeyPem;
  }

  public void setHttpsClientKeyPem(String httpsClientKeyPem) {
    this.httpsClientKeyPem = httpsClientKeyPem;
  }

  public boolean isHttpsInsecureSkipVerify() {
    return httpsInsecureSkipVerify;
  }

  public void setHttpsInsecureSkipVerify(boolean httpsInsecureSkipVerify) {
    this.httpsInsecureSkipVerify = httpsInsecureSkipVerify;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
