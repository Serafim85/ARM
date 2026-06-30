package com.networkscanner.backend.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_telemetry_snapshot")
public class MonitoringTelemetrySnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false, unique = true)
  private MonitoredDeviceEntity device;

  @Column(name = "cpu_current")
  private Double cpuCurrent;

  @Column(name = "cpu_average")
  private Double cpuAverage;

  @Column(name = "cpu_peak")
  private Double cpuPeak;

  @Column(name = "cpu_current_item_name", length = 512)
  private String cpuCurrentItemName;

  @Column(name = "cpu_average_item_name", length = 512)
  private String cpuAverageItemName;

  @Column(name = "cpu_peak_item_name", length = 512)
  private String cpuPeakItemName;

  @Column(name = "ram_used_percent")
  private Integer ramUsedPercent;

  @Column(name = "rom_used_percent")
  private Integer romUsedPercent;

  @Column(name = "uptime", nullable = false)
  private String uptime;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "admin_contact", nullable = false)
  private String adminContact;

  @Column(name = "hardware_version", nullable = false)
  private String hardwareVersion;

  @Column(name = "location", nullable = false)
  private String location;

  @Column(name = "added_at", nullable = false)
  private String addedAt;

  @Column(name = "boot_version", nullable = false)
  private String bootVersion;

  @Column(name = "collected_at", nullable = false)
  private OffsetDateTime collectedAt;

  @Column(name = "source", nullable = false, length = 64)
  private String source;

  @Column(name = "live_mode", nullable = false)
  private boolean liveMode;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public MonitoredDeviceEntity getDevice() {
    return device;
  }

  public void setDevice(MonitoredDeviceEntity device) {
    this.device = device;
  }

  public Double getCpuCurrent() {
    return cpuCurrent;
  }

  public void setCpuCurrent(Double cpuCurrent) {
    this.cpuCurrent = cpuCurrent;
  }

  public Double getCpuAverage() {
    return cpuAverage;
  }

  public void setCpuAverage(Double cpuAverage) {
    this.cpuAverage = cpuAverage;
  }

  public Double getCpuPeak() {
    return cpuPeak;
  }

  public void setCpuPeak(Double cpuPeak) {
    this.cpuPeak = cpuPeak;
  }

  public String getCpuCurrentItemName() {
    return cpuCurrentItemName;
  }

  public void setCpuCurrentItemName(String cpuCurrentItemName) {
    this.cpuCurrentItemName = cpuCurrentItemName;
  }

  public String getCpuAverageItemName() {
    return cpuAverageItemName;
  }

  public void setCpuAverageItemName(String cpuAverageItemName) {
    this.cpuAverageItemName = cpuAverageItemName;
  }

  public String getCpuPeakItemName() {
    return cpuPeakItemName;
  }

  public void setCpuPeakItemName(String cpuPeakItemName) {
    this.cpuPeakItemName = cpuPeakItemName;
  }

  public Integer getRamUsedPercent() {
    return ramUsedPercent;
  }

  public void setRamUsedPercent(Integer ramUsedPercent) {
    this.ramUsedPercent = ramUsedPercent;
  }

  public Integer getRomUsedPercent() {
    return romUsedPercent;
  }

  public void setRomUsedPercent(Integer romUsedPercent) {
    this.romUsedPercent = romUsedPercent;
  }

  public String getUptime() {
    return uptime;
  }

  public void setUptime(String uptime) {
    this.uptime = uptime;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAdminContact() {
    return adminContact;
  }

  public void setAdminContact(String adminContact) {
    this.adminContact = adminContact;
  }

  public String getHardwareVersion() {
    return hardwareVersion;
  }

  public void setHardwareVersion(String hardwareVersion) {
    this.hardwareVersion = hardwareVersion;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getAddedAt() {
    return addedAt;
  }

  public void setAddedAt(String addedAt) {
    this.addedAt = addedAt;
  }

  public String getBootVersion() {
    return bootVersion;
  }

  public void setBootVersion(String bootVersion) {
    this.bootVersion = bootVersion;
  }

  public OffsetDateTime getCollectedAt() {
    return collectedAt;
  }

  public void setCollectedAt(OffsetDateTime collectedAt) {
    this.collectedAt = collectedAt;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public boolean isLiveMode() {
    return liveMode;
  }

  public void setLiveMode(boolean liveMode) {
    this.liveMode = liveMode;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
