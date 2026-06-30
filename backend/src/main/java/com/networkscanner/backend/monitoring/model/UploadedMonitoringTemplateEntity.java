package com.networkscanner.backend.monitoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "uploaded_monitoring_templates")
public class UploadedMonitoringTemplateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "template_id", nullable = false, unique = true, length = 128)
  private String templateId;

  @Column(name = "extends_template", length = 128)
  private String extendsTemplate;

  @Column(name = "vendor", length = 255)
  private String vendor;

  @Column(name = "model", length = 255)
  private String model;

  @Column(name = "model_regex", length = 512)
  private String modelRegex;

  @Column(name = "firmware", length = 255)
  private String firmware;

  @Column(name = "priority")
  private Integer priority;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "manifest_yaml", nullable = false, columnDefinition = "TEXT")
  private String manifestYaml;

  @Column(name = "template_file_name", nullable = false, length = 255)
  private String templateFileName;

  @Column(name = "template_yaml", nullable = false, columnDefinition = "TEXT")
  private String templateYaml;

  @Column(name = "uploaded_by", length = 255)
  private String uploadedBy;

  @Column(name = "uploaded_at", nullable = false)
  private OffsetDateTime uploadedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public String getExtendsTemplate() {
    return extendsTemplate;
  }

  public void setExtendsTemplate(String extendsTemplate) {
    this.extendsTemplate = extendsTemplate;
  }

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getModelRegex() {
    return modelRegex;
  }

  public void setModelRegex(String modelRegex) {
    this.modelRegex = modelRegex;
  }

  public String getFirmware() {
    return firmware;
  }

  public void setFirmware(String firmware) {
    this.firmware = firmware;
  }

  public Integer getPriority() {
    return priority;
  }

  public void setPriority(Integer priority) {
    this.priority = priority;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public String getManifestYaml() {
    return manifestYaml;
  }

  public void setManifestYaml(String manifestYaml) {
    this.manifestYaml = manifestYaml;
  }

  public String getTemplateFileName() {
    return templateFileName;
  }

  public void setTemplateFileName(String templateFileName) {
    this.templateFileName = templateFileName;
  }

  public String getTemplateYaml() {
    return templateYaml;
  }

  public void setTemplateYaml(String templateYaml) {
    this.templateYaml = templateYaml;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(String uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public OffsetDateTime getUploadedAt() {
    return uploadedAt;
  }

  public void setUploadedAt(OffsetDateTime uploadedAt) {
    this.uploadedAt = uploadedAt;
  }
}
