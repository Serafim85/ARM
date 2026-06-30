package com.networkscanner.backend.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "baseline_configs")
public class BaselineConfigEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "device_ip", nullable = false, unique = true)
  private String deviceIp;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "configured_at", nullable = false)
  private OffsetDateTime configuredAt;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  public Long getId() {
    return id;
  }

  public String getDeviceIp() {
    return deviceIp;
  }

  public void setDeviceIp(String deviceIp) {
    this.deviceIp = deviceIp;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public OffsetDateTime getConfiguredAt() {
    return configuredAt;
  }

  public void setConfiguredAt(OffsetDateTime configuredAt) {
    this.configuredAt = configuredAt;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
