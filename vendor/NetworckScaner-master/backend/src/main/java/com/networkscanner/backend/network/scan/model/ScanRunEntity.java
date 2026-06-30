package com.networkscanner.backend.network.scan.model;

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
@Table(name = "scan_runs")
@BatchSize(size = 32)
public class ScanRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScanRunSource source;

  @Column(name = "scan_job_id")
  private Long scanJobId;

  @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
  private String requestJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScanRunStatus status;

  @Column(name = "total_addresses", nullable = false)
  private int totalAddresses;

  @Column(name = "scanned_addresses", nullable = false)
  private int scannedAddresses;

  @Column(name = "found_count", nullable = false)
  private int foundCount;

  @Column(name = "result_json", columnDefinition = "TEXT")
  private String resultJson;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

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

  public ScanRunSource getSource() {
    return source;
  }

  public void setSource(ScanRunSource source) {
    this.source = source;
  }

  public Long getScanJobId() {
    return scanJobId;
  }

  public void setScanJobId(Long scanJobId) {
    this.scanJobId = scanJobId;
  }

  public String getRequestJson() {
    return requestJson;
  }

  public void setRequestJson(String requestJson) {
    this.requestJson = requestJson;
  }

  public ScanRunStatus getStatus() {
    return status;
  }

  public void setStatus(ScanRunStatus status) {
    this.status = status;
  }

  public int getTotalAddresses() {
    return totalAddresses;
  }

  public void setTotalAddresses(int totalAddresses) {
    this.totalAddresses = totalAddresses;
  }

  public int getScannedAddresses() {
    return scannedAddresses;
  }

  public void setScannedAddresses(int scannedAddresses) {
    this.scannedAddresses = scannedAddresses;
  }

  public int getFoundCount() {
    return foundCount;
  }

  public void setFoundCount(int foundCount) {
    this.foundCount = foundCount;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(OffsetDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public OffsetDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(OffsetDateTime finishedAt) {
    this.finishedAt = finishedAt;
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
