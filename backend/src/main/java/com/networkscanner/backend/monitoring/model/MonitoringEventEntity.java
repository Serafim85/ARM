package com.networkscanner.backend.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_events")
public class MonitoringEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "device_id", nullable = false)
  private MonitoredDeviceEntity device;

  @Column(name = "template_id")
  private String templateId;

  @Column(name = "template_version")
  private String templateVersion;

  @Column(name = "pack_version")
  private String packVersion;

  @Column(name = "metric_name", nullable = false)
  private String metricName;

  @Column(name = "trigger_uuid")
  private String triggerUuid;

  @Column(name = "trigger_name")
  private String triggerName;

  @Column(name = "instance_key")
  private String instanceKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "threshold_level", nullable = false)
  private ThresholdLevel thresholdLevel;

  @Column(name = "threshold_value", nullable = false)
  private double thresholdValue;

  @Column(name = "actual_value", nullable = false)
  private double actualValue;

  @Column(name = "breach_started_at", nullable = false)
  private OffsetDateTime breachStartedAt;

  @Column(name = "normalized_at")
  private OffsetDateTime normalizedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MonitoringEventStatus status;

  @Column(name = "severity")
  private String severity;

  @Column(name = "trigger_expression")
  private String triggerExpression;

  @Column(name = "recovery_expression")
  private String recoveryExpression;

  @Column(name = "recovery_path")
  private String recoveryPath;

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

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
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

  public String getMetricName() {
    return metricName;
  }

  public void setMetricName(String metricName) {
    this.metricName = metricName;
  }

  public String getTriggerUuid() {
    return triggerUuid;
  }

  public void setTriggerUuid(String triggerUuid) {
    this.triggerUuid = triggerUuid;
  }

  public String getTriggerName() {
    return triggerName;
  }

  public void setTriggerName(String triggerName) {
    this.triggerName = triggerName;
  }

  public String getInstanceKey() {
    return instanceKey;
  }

  public void setInstanceKey(String instanceKey) {
    this.instanceKey = instanceKey;
  }

  public ThresholdLevel getThresholdLevel() {
    return thresholdLevel;
  }

  public void setThresholdLevel(ThresholdLevel thresholdLevel) {
    this.thresholdLevel = thresholdLevel;
  }

  public double getThresholdValue() {
    return thresholdValue;
  }

  public void setThresholdValue(double thresholdValue) {
    this.thresholdValue = thresholdValue;
  }

  public double getActualValue() {
    return actualValue;
  }

  public void setActualValue(double actualValue) {
    this.actualValue = actualValue;
  }

  public OffsetDateTime getBreachStartedAt() {
    return breachStartedAt;
  }

  public void setBreachStartedAt(OffsetDateTime breachStartedAt) {
    this.breachStartedAt = breachStartedAt;
  }

  public OffsetDateTime getNormalizedAt() {
    return normalizedAt;
  }

  public void setNormalizedAt(OffsetDateTime normalizedAt) {
    this.normalizedAt = normalizedAt;
  }

  public MonitoringEventStatus getStatus() {
    return status;
  }

  public void setStatus(MonitoringEventStatus status) {
    this.status = status;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getTriggerExpression() {
    return triggerExpression;
  }

  public void setTriggerExpression(String triggerExpression) {
    this.triggerExpression = triggerExpression;
  }

  public String getRecoveryExpression() {
    return recoveryExpression;
  }

  public void setRecoveryExpression(String recoveryExpression) {
    this.recoveryExpression = recoveryExpression;
  }

  public String getRecoveryPath() {
    return recoveryPath;
  }

  public void setRecoveryPath(String recoveryPath) {
    this.recoveryPath = recoveryPath;
  }
}
