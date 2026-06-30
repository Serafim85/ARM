package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifest;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifestEntry;
import com.networkscanner.backend.monitoring.dto.UploadedMonitoringTemplatePackage;
import com.networkscanner.backend.monitoring.dto.ZabbixExportDocument;
import com.networkscanner.backend.monitoring.dto.ZabbixTemplateRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MonitoringTemplateArchiveReader {

  static final int MAX_TEMPLATE_FILE_BYTES = 10 * 1024 * 1024;

  private static final Pattern TEMPLATE_ID_SANITIZER = Pattern.compile("[^a-z0-9-]+");

  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final MonitoringTemplateObfuscator templateObfuscator;

  public MonitoringTemplateArchiveReader(MonitoringTemplateObfuscator templateObfuscator) {
    this.templateObfuscator = templateObfuscator;
    yamlMapper.findAndRegisterModules();
  }

  public UploadedMonitoringTemplatePackage readSingleTemplatePackage(String originalFilename, byte[] archiveBytes) {
    if (archiveBytes == null || archiveBytes.length == 0) {
      throw new IllegalArgumentException("Файл шаблона пуст.");
    }
    if (!looksLikeTemplateFile(originalFilename)) {
      throw new IllegalArgumentException("Поддерживается только файл .template");
    }
    if (archiveBytes.length > MAX_TEMPLATE_FILE_BYTES) {
      throw new IllegalArgumentException("Размер файла шаблона не должен превышать 10 MB.");
    }

    final String templateYaml;
    try {
      templateYaml = templateObfuscator.decodeUtf8(archiveBytes);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Повреждён файл шаблона", exception);
    }
    return readSingleTemplateYamlPackage(originalFilename, templateYaml);
  }

  private UploadedMonitoringTemplatePackage readSingleTemplateYamlPackage(
      String originalFilename,
      String templateYaml
  ) {
    JsonNode root;
    try {
      root = yamlMapper.readTree(templateYaml);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Не удалось разобрать YAML шаблона " + safeName(originalFilename) + ".",
          exception
      );
    }
    JsonNode exportNode = root.path("zabbix_export");
    if (exportNode.isMissingNode() || !exportNode.isObject()) {
      throw new IllegalArgumentException("YAML не содержит объекта zabbix_export.");
    }
    JsonNode templatesNode = exportNode.path("templates");
    if (!templatesNode.isArray() || templatesNode.isEmpty()) {
      throw new IllegalArgumentException("YAML не содержит zabbix_export.templates.");
    }

    ZabbixExportDocument document;
    try {
      document = yamlMapper.treeToValue(root, ZabbixExportDocument.class);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Не удалось разобрать YAML шаблона " + safeName(originalFilename) + ".",
          exception
      );
    }
    ZabbixExportDocument.ZabbixExportPayload payload = document.zabbixExport();
    if (payload == null || payload.templates() == null || payload.templates().isEmpty()) {
      throw new IllegalArgumentException("YAML не содержит zabbix_export.templates.");
    }

    int primaryIndex =
        selectPrimaryTemplateIndex(templatesNode, originalFilename, payload.templates().size());
    ZabbixTemplateRecord primaryTemplate = payload.templates().get(primaryIndex);

    String rawTemplateName = firstNonBlank(
        primaryTemplate.template(),
        firstNonBlank(primaryTemplate.name(), "zabbix-template")
    );
    String templateId = sanitizeTemplateId(rawTemplateName);
    String templateFileName = resolveYamlFileName(originalFilename, templateId);
    String templateVersion = firstNonBlank(payload.version(), "1.0.0");

    MonitoringTemplateManifest manifest = new MonitoringTemplateManifest(
        "1",
        "uploaded-yaml-" + LocalDate.now(),
        templateId,
        java.util.List.of(new MonitoringTemplateManifestEntry(
            templateId,
            templateFileName,
            templateVersion,
            "SNMP",
            null,
            null,
            null,
            0,
            null,
            null,
            rawTemplateName,
            null
        ))
    );

    try {
      String manifestYaml = yamlMapper.writeValueAsString(manifest);
      return new UploadedMonitoringTemplatePackage(
          templateId,
          null,
          manifestYaml,
          templateFileName,
          templateYaml
      );
    } catch (IOException exception) {
      throw new IllegalArgumentException("Не удалось сформировать внутренний manifest для YAML шаблона.", exception);
    }
  }

  private int selectPrimaryTemplateIndex(
      JsonNode templatesNode, String originalFilename, int templateCount) {
    List<Integer> linkedIndices = new ArrayList<>();
    for (int i = 0; i < templatesNode.size(); i++) {
      if (templateHasLinkedParents(templatesNode.get(i))) {
        linkedIndices.add(i);
      }
    }
    if (linkedIndices.size() == 1) {
      return linkedIndices.get(0);
    }
    String stem = filenameStem(originalFilename);
    int byFile = matchTemplateIndexByFilename(templatesNode, stem);
    if (byFile >= 0) {
      return byFile;
    }
    if (linkedIndices.size() > 1) {
      for (int idx : linkedIndices) {
        if (templateAtIndexMatchesStem(templatesNode.get(idx), stem)) {
          return idx;
        }
      }
      throw new IllegalArgumentException(
          "Укажите экспорт с одним целевым шаблоном. Обнаружено: " + describeTemplates(templatesNode));
    }
    if (templateCount == 1) {
      return 0;
    }
    throw new IllegalArgumentException(
        "Укажите экспорт с одним целевым шаблоном или переименуйте файл под имя шаблона. Обнаружено: "
            + describeTemplates(templatesNode));
  }

  private static boolean templateHasLinkedParents(JsonNode templateNode) {
    JsonNode linked = templateNode.path("templates");
    if (!linked.isArray() || linked.isEmpty()) {
      return false;
    }
    for (JsonNode link : linked) {
      if (link.hasNonNull("name") && !link.path("name").asText("").isBlank()) {
        return true;
      }
    }
    return false;
  }

  private int matchTemplateIndexByFilename(JsonNode templatesNode, String stem) {
    if (stem.isBlank()) {
      return -1;
    }
    for (int i = 0; i < templatesNode.size(); i++) {
      if (templateAtIndexMatchesStem(templatesNode.get(i), stem)) {
        return i;
      }
    }
    return -1;
  }

  private boolean templateAtIndexMatchesStem(JsonNode templateNode, String stem) {
    if (stem.isBlank()) {
      return false;
    }
    String t = firstNonBlank(
        templateNode.path("template").asText("").trim(),
        templateNode.path("name").asText("").trim());
    if (t.isBlank()) {
      return false;
    }
    String stemKey = templateIdFromStemForMatch(stem);
    return stemKey.equals(sanitizeTemplateId(t));
  }

  private static String filenameStem(String originalFilename) {
    String safe = originalFilename == null || originalFilename.isBlank() ? "" : originalFilename.trim();
    int slash = Math.max(safe.lastIndexOf('/'), safe.lastIndexOf('\\'));
    if (slash >= 0) {
      safe = safe.substring(slash + 1);
    }
    String lower = safe.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".template")) {
      return safe.substring(0, safe.length() - 9);
    }
    if (lower.endsWith(".yaml")) {
      return safe.substring(0, safe.length() - 5);
    }
    if (lower.endsWith(".yml")) {
      return safe.substring(0, safe.length() - 4);
    }
    return safe;
  }

  private String templateIdFromStemForMatch(String stem) {
    String id = sanitizeTemplateId(stem);
    if (id.startsWith("template-")) {
      return id.substring("template-".length());
    }
    return id;
  }

  private String describeTemplates(JsonNode templatesNode) {
    List<String> names = new ArrayList<>();
    for (JsonNode n : templatesNode) {
      String t = n.path("template").asText("").trim();
      String nm = n.path("name").asText("").trim();
      names.add(firstNonBlank(t, firstNonBlank(nm, "?")));
    }
    return String.join(", ", names);
  }

  private String safeName(String originalFilename) {
    return originalFilename == null || originalFilename.isBlank() ? "template.template" : originalFilename;
  }

  private boolean looksLikeTemplateFile(String originalFilename) {
    String safe = safeName(originalFilename).toLowerCase(Locale.ROOT);
    return safe.endsWith(".template");
  }

  private String resolveYamlFileName(String originalFilename, String templateId) {
    String safe = safeName(originalFilename).trim();
    if (safe.toLowerCase(Locale.ROOT).endsWith(".yaml") || safe.toLowerCase(Locale.ROOT).endsWith(".yml")) {
      return safe;
    }
    return templateId + ".yaml";
  }

  private String sanitizeTemplateId(String source) {
    String base = source == null ? "" : source.trim().toLowerCase();
    base = base.replace('_', '-');
    String sanitized = TEMPLATE_ID_SANITIZER.matcher(base).replaceAll("-");
    sanitized = sanitized.replaceAll("-{2,}", "-");
    sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
    return sanitized.isBlank() ? "zabbix-template" : sanitized;
  }

  private String firstNonBlank(String value, String fallback) {
    return isBlank(value) ? fallback : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
