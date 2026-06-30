package com.networkscanner.backend.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(
    name = "monitored_devices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_monitored_devices_ip_snmp_port", columnNames = {"ip", "snmp_port"})
    }
)
@BatchSize(size = 64)
public class MonitoredDeviceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String ip;

  @Column(name = "host_name", nullable = false)
  private String hostName;

  @Column(name = "domain_name", nullable = false)
  private String domainName = "-";

  @Column(nullable = false)
  private String name;

  @Column(name = "serial_number", nullable = false)
  private String serialNumber;

  @Column(name = "mac_address", nullable = false)
  private String macAddress;

  @Column(nullable = false)
  private String vendor;

  @Column(nullable = false)
  private String model;

  @Column(name = "firmware_version", nullable = false)
  private String firmwareVersion;

  @Column(name = "polling_status", nullable = false)
  private String pollingStatus;

  @Column(nullable = false)
  private String status;

  @Enumerated(EnumType.STRING)
  @Column(name = "health_status", nullable = false)
  private DeviceHealthStatus healthStatus;

  @Column(name = "group_name", nullable = false)
  private String groupName;

  @Column(name = "tags_json", nullable = false, columnDefinition = "TEXT")
  private String tagsJson = "[]";

  @Column(name = "availability_json", nullable = false, columnDefinition = "TEXT")
  private String availabilityJson;

  @Column(name = "template_id")
  private String templateId;

  @Column(name = "effective_template_id")
  private String effectiveTemplateId;

  @Column(name = "template_ids")
  private String templateIds;

  @Column(name = "template_version")
  private String templateVersion;

  @Column(name = "pack_version")
  private String packVersion;

  @Column(name = "schema_version")
  private String schemaVersion;

  @Column(name = "snmp_port")
  private Integer snmpPort;

  @Column(name = "snmp_version")
  private String snmpVersion;

  @Column(name = "snmp_community")
  private String snmpCommunity;

  @Column(name = "snmp_security_username")
  private String snmpSecurityUsername;

  @Column(name = "snmp_auth_protocol")
  private String snmpAuthProtocol;

  @Column(name = "snmp_auth_password")
  private String snmpAuthPassword;

  @Column(name = "snmp_privacy_protocol")
  private String snmpPrivacyProtocol;

  @Column(name = "snmp_privacy_password")
  private String snmpPrivacyPassword;

  @Column(name = "item_allowlist_initialized", nullable = false)
  private boolean itemAllowlistInitialized;

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

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public String getDomainName() {
    return domainName;
  }

  public void setDomainName(String domainName) {
    this.domainName = domainName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String serialNumber) {
    this.serialNumber = serialNumber;
  }

  public String getMacAddress() {
    return macAddress;
  }

  public void setMacAddress(String macAddress) {
    this.macAddress = macAddress;
  }

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getFirmwareVersion() {
    return firmwareVersion;
  }

  public void setFirmwareVersion(String firmwareVersion) {
    this.firmwareVersion = firmwareVersion;
  }

  public String getPollingStatus() {
    return pollingStatus;
  }

  public void setPollingStatus(String pollingStatus) {
    this.pollingStatus = pollingStatus;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public DeviceHealthStatus getHealthStatus() {
    return healthStatus;
  }

  public void setHealthStatus(DeviceHealthStatus healthStatus) {
    this.healthStatus = healthStatus;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getTagsJson() {
    return tagsJson;
  }

  public void setTagsJson(String tagsJson) {
    this.tagsJson = tagsJson;
  }

  public String getAvailabilityJson() {
    return availabilityJson;
  }

  public void setAvailabilityJson(String availabilityJson) {
    this.availabilityJson = availabilityJson;
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

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public String getEffectiveTemplateId() {
    return effectiveTemplateId;
  }

  public void setEffectiveTemplateId(String effectiveTemplateId) {
    this.effectiveTemplateId = effectiveTemplateId;
  }

  public String getTemplateIds() {
    return templateIds;
  }

  public void setTemplateIds(String templateIds) {
    this.templateIds = templateIds;
  }

  public String getTemplateVersion() {
    return templateVersion;
  }

  public void setTemplateVersion(String templateVersion) {
    this.templateVersion = templateVersion;
  }

  public String getPackVersion() {
    return packVersion;
  }

  public void setPackVersion(String packVersion) {
    this.packVersion = packVersion;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public Integer getSnmpPort() {
    return snmpPort;
  }

  public void setSnmpPort(Integer snmpPort) {
    this.snmpPort = snmpPort;
  }

  public String getSnmpVersion() {
    return snmpVersion;
  }

  public void setSnmpVersion(String snmpVersion) {
    this.snmpVersion = snmpVersion;
  }

  public String getSnmpCommunity() {
    return snmpCommunity;
  }

  public void setSnmpCommunity(String snmpCommunity) {
    this.snmpCommunity = snmpCommunity;
  }

  public String getSnmpSecurityUsername() {
    return snmpSecurityUsername;
  }

  public void setSnmpSecurityUsername(String snmpSecurityUsername) {
    this.snmpSecurityUsername = snmpSecurityUsername;
  }

  public String getSnmpAuthProtocol() {
    return snmpAuthProtocol;
  }

  public void setSnmpAuthProtocol(String snmpAuthProtocol) {
    this.snmpAuthProtocol = snmpAuthProtocol;
  }

  public String getSnmpAuthPassword() {
    return snmpAuthPassword;
  }

  public void setSnmpAuthPassword(String snmpAuthPassword) {
    this.snmpAuthPassword = snmpAuthPassword;
  }

  public String getSnmpPrivacyProtocol() {
    return snmpPrivacyProtocol;
  }

  public void setSnmpPrivacyProtocol(String snmpPrivacyProtocol) {
    this.snmpPrivacyProtocol = snmpPrivacyProtocol;
  }

  public String getSnmpPrivacyPassword() {
    return snmpPrivacyPassword;
  }

  public void setSnmpPrivacyPassword(String snmpPrivacyPassword) {
    this.snmpPrivacyPassword = snmpPrivacyPassword;
  }

  public boolean isItemAllowlistInitialized() {
    return itemAllowlistInitialized;
  }

  public void setItemAllowlistInitialized(boolean itemAllowlistInitialized) {
    this.itemAllowlistInitialized = itemAllowlistInitialized;
  }
}
