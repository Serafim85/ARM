package com.networkscanner.backend.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitoring_template_priority_overrides")
public class MonitoringTemplatePriorityOverrideEntity {

  @Id
  @Column(name = "template_id", nullable = false, length = 128)
  private String templateId;

  @Column(name = "priority", nullable = false)
  private int priority;

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }
}
