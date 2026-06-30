package com.networkscanner.backend.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notification_subscriptions")
public class NotificationSubscriptionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "notification_kind", nullable = false, length = 32)
  private String notificationKind;

  @Column(name = "subscription_type", nullable = false, length = 32)
  private String subscriptionType;

  @Column(nullable = false, length = 16)
  private String channel;

  @Column(name = "event_code", nullable = false, length = 512)
  private String eventCode;

  @Column(name = "recipient_email", nullable = false, length = 1024)
  private String recipientEmail;

  @Column(name = "owner_email", nullable = false, length = 255)
  private String ownerEmail;

  @Column(name = "device_ip_filter", length = 2048)
  private String deviceIpFilter;

  @Column(name = "device_tag_filter", length = 512)
  private String deviceTagFilter;

  @Column(name = "severity_filter", length = 128)
  private String severityFilter;

  @Column(name = "metric_filter", length = 2048)
  private String metricFilter;

  @Column(name = "custom_condition", length = 2048)
  private String customCondition;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getNotificationKind() { return notificationKind; }
  public void setNotificationKind(String notificationKind) { this.notificationKind = notificationKind; }
  public String getSubscriptionType() { return subscriptionType; }
  public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }
  public String getEventCode() { return eventCode; }
  public void setEventCode(String eventCode) { this.eventCode = eventCode; }
  public String getRecipientEmail() { return recipientEmail; }
  public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
  public String getOwnerEmail() { return ownerEmail; }
  public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
  public String getDeviceIpFilter() { return deviceIpFilter; }
  public void setDeviceIpFilter(String deviceIpFilter) { this.deviceIpFilter = deviceIpFilter; }
  public String getDeviceTagFilter() { return deviceTagFilter; }
  public void setDeviceTagFilter(String deviceTagFilter) { this.deviceTagFilter = deviceTagFilter; }
  public String getSeverityFilter() { return severityFilter; }
  public void setSeverityFilter(String severityFilter) { this.severityFilter = severityFilter; }
  public String getMetricFilter() { return metricFilter; }
  public void setMetricFilter(String metricFilter) { this.metricFilter = metricFilter; }
  public String getCustomCondition() { return customCondition; }
  public void setCustomCondition(String customCondition) { this.customCondition = customCondition; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
