package com.networkscanner.backend.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "device_backups")
public class DeviceBackupEntity {

  @Id
  private String id;

  @Column(name = "device_ip", nullable = false)
  private String deviceIp;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private String source;

  @Column(nullable = false)
  private String size;

  @Column(nullable = false)
  private String status;

  @Column(name = "baseline_status", nullable = false)
  private String baselineStatus;

  @Column(name = "comparison_summary", columnDefinition = "TEXT")
  private String comparisonSummary;

  @Column(name = "compared_at")
  private OffsetDateTime comparedAt;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDeviceIp() {
    return deviceIp;
  }

  public void setDeviceIp(String deviceIp) {
    this.deviceIp = deviceIp;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getBaselineStatus() {
    return baselineStatus;
  }

  public void setBaselineStatus(String baselineStatus) {
    this.baselineStatus = baselineStatus;
  }

  public String getComparisonSummary() {
    return comparisonSummary;
  }

  public void setComparisonSummary(String comparisonSummary) {
    this.comparisonSummary = comparisonSummary;
  }

  public OffsetDateTime getComparedAt() {
    return comparedAt;
  }

  public void setComparedAt(OffsetDateTime comparedAt) {
    this.comparedAt = comparedAt;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
