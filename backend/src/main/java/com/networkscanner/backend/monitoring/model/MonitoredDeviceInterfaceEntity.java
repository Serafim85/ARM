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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "monitored_device_interfaces",
    uniqueConstraints = @UniqueConstraint(name = "uq_monitored_device_interfaces_device_name", columnNames = {"device_id", "name"})
)
public class MonitoredDeviceInterfaceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private MonitoredDeviceEntity device;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column(name = "admin_status", nullable = false)
  private String adminStatus;

  @Column(name = "oper_status", nullable = false)
  private String operStatus;

  @Column(nullable = false)
  private String lost;

  @Column(name = "nominal_speed", nullable = false)
  private String nominalSpeed;

  @Column(name = "active_speed", nullable = false)
  private String activeSpeed;

  @Column(nullable = false)
  private String purpose;

  @Column(nullable = false)
  private String mode;

  @Column(nullable = false)
  private String kind;

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

  public String getAdminStatus() {
    return adminStatus;
  }

  public void setAdminStatus(String adminStatus) {
    this.adminStatus = adminStatus;
  }

  public String getOperStatus() {
    return operStatus;
  }

  public void setOperStatus(String operStatus) {
    this.operStatus = operStatus;
  }

  public String getLost() {
    return lost;
  }

  public void setLost(String lost) {
    this.lost = lost;
  }

  public String getNominalSpeed() {
    return nominalSpeed;
  }

  public void setNominalSpeed(String nominalSpeed) {
    this.nominalSpeed = nominalSpeed;
  }

  public String getActiveSpeed() {
    return activeSpeed;
  }

  public void setActiveSpeed(String activeSpeed) {
    this.activeSpeed = activeSpeed;
  }

  public String getPurpose() {
    return purpose;
  }

  public void setPurpose(String purpose) {
    this.purpose = purpose;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
