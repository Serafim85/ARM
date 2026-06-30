package com.networkscanner.backend.network.scanjobs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "scan_jobs")
@BatchSize(size = 32)
public class ScanJobEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(nullable = false)
  private String cron;

  @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
  private String requestJson;

  @Column(name = "last_run_at")
  private OffsetDateTime lastRunAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_status")
  private ScanJobStatus lastStatus;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "last_result_json", columnDefinition = "TEXT")
  private String lastResultJson;

  @Column(name = "last_result_count", nullable = false)
  private int lastResultCount;

  @Column(name = "active_run_id")
  private Long activeRunId;

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

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public String getRequestJson() {
    return requestJson;
  }

  public void setRequestJson(String requestJson) {
    this.requestJson = requestJson;
  }

  public OffsetDateTime getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(OffsetDateTime lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public ScanJobStatus getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(ScanJobStatus lastStatus) {
    this.lastStatus = lastStatus;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public String getLastResultJson() {
    return lastResultJson;
  }

  public void setLastResultJson(String lastResultJson) {
    this.lastResultJson = lastResultJson;
  }

  public int getLastResultCount() {
    return lastResultCount;
  }

  public void setLastResultCount(int lastResultCount) {
    this.lastResultCount = lastResultCount;
  }

  public Long getActiveRunId() {
    return activeRunId;
  }

  public void setActiveRunId(Long activeRunId) {
    this.activeRunId = activeRunId;
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

