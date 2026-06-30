package com.networkscanner.backend.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitored_device_items")
@IdClass(MonitoredDeviceItemEntityId.class)
public class MonitoredDeviceItemEntity {

  @Id
  @Column(name = "device_id", nullable = false)
  private Long deviceId;

  @Id
  @Column(name = "item_uuid", nullable = false, length = 32)
  private String itemUuid;

  @Id
  @Column(name = "instance_key", nullable = false, length = 255)
  private String instanceKey;

  @Column(name = "item_key", nullable = false, length = 255)
  private String itemKey;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "item_type", nullable = false, length = 64)
  private String itemType;

  @Column(name = "discovery_prototype", nullable = false)
  private boolean discoveryPrototype;

  @Column(name = "discovery_rule_key", length = 255)
  private String discoveryRuleKey;

  @Column(name = "source_template_id", length = 128)
  private String sourceTemplateId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Long getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(Long deviceId) {
    this.deviceId = deviceId;
  }

  public String getItemUuid() {
    return itemUuid;
  }

  public void setItemUuid(String itemUuid) {
    this.itemUuid = itemUuid;
  }

  public String getInstanceKey() {
    return instanceKey;
  }

  public void setInstanceKey(String instanceKey) {
    this.instanceKey = instanceKey;
  }

  public String getItemKey() {
    return itemKey;
  }

  public void setItemKey(String itemKey) {
    this.itemKey = itemKey;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getItemType() {
    return itemType;
  }

  public void setItemType(String itemType) {
    this.itemType = itemType;
  }

  public boolean isDiscoveryPrototype() {
    return discoveryPrototype;
  }

  public void setDiscoveryPrototype(boolean discoveryPrototype) {
    this.discoveryPrototype = discoveryPrototype;
  }

  public String getDiscoveryRuleKey() {
    return discoveryRuleKey;
  }

  public void setDiscoveryRuleKey(String discoveryRuleKey) {
    this.discoveryRuleKey = discoveryRuleKey;
  }

  public String getSourceTemplateId() {
    return sourceTemplateId;
  }

  public void setSourceTemplateId(String sourceTemplateId) {
    this.sourceTemplateId = sourceTemplateId;
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
