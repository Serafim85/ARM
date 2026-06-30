package com.networkscanner.backend.monitoring.dto;

public record UploadedMonitoringTemplatePackage(
    String templateId,
    String extendsTemplate,
    String manifestYaml,
    String templateFileName,
    String templateYaml
) {
}
