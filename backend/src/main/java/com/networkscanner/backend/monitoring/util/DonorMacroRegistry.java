package com.networkscanner.backend.monitoring.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networkscanner.backend.monitoring.dto.ZabbixMacroRecord;
import com.networkscanner.backend.monitoring.impl.MonitoringTemplateObfuscator;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class DonorMacroRegistry {

  private static final Logger log = LoggerFactory.getLogger(DonorMacroRegistry.class);

  private static final String MODULES_PATTERN = "classpath*:monitoring-templates/modules/*.template";
  private static final String MODULE_PREFIX = "module:";

  private final Map<String, Map<String, String>> donorsByRef;

  public DonorMacroRegistry(MonitoringTemplateObfuscator obfuscator) {
    this(obfuscator, new PathMatchingResourcePatternResolver());
  }

  DonorMacroRegistry(MonitoringTemplateObfuscator obfuscator, PathMatchingResourcePatternResolver resourceResolver) {
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    yamlMapper.findAndRegisterModules();
    Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
    try {
      Resource[] resources = resourceResolver.getResources(MODULES_PATTERN);
      for (Resource resource : resources) {
        if (resource == null || !resource.exists()) {
          continue;
        }
        registerResource(obfuscator, yamlMapper, loaded, resource);
      }
    } catch (IOException exception) {
      log.warn("Failed to scan donor macro modules: {}", exception.getMessage());
    }
    this.donorsByRef = Map.copyOf(loaded);
    log.info("Loaded donor macro registry with {} reference key(s)", loaded.size());
  }

  public Map<String, String> resolve(String donorRef) {
    if (donorRef == null || donorRef.isBlank()) {
      return Map.of();
    }
    Map<String, String> macros = donorsByRef.get(normalizeRef(donorRef));
    return macros == null ? Map.of() : macros;
  }

  public void putAllFromRefs(List<String> refs, Map<String, String> target) {
    if (refs == null || refs.isEmpty() || target == null) {
      return;
    }
    for (String ref : refs) {
      target.putAll(resolve(ref));
    }
  }

  public boolean hasDonor(String donorRef) {
    return donorRef != null && !donorRef.isBlank() && donorsByRef.containsKey(normalizeRef(donorRef));
  }

  public Map<String, Map<String, String>> allDonors() {
    return donorsByRef;
  }

  private void registerResource(
      MonitoringTemplateObfuscator obfuscator,
      ObjectMapper yamlMapper,
      Map<String, Map<String, String>> loaded,
      Resource resource
  ) throws IOException {
    String filename = resource.getFilename();
    if (filename == null || filename.isBlank()) {
      return;
    }
    String stem = filename.endsWith(".template")
        ? filename.substring(0, filename.length() - ".template".length())
        : filename;
    String shortId = stemToShortId(stem);
    try (InputStream inputStream = resource.getInputStream()) {
      String yaml = obfuscator.decodeUtf8(inputStream.readAllBytes());
      JsonNode exportNode = yamlMapper.readTree(yaml).path("zabbix_export");
      JsonNode templatesNode = exportNode.path("templates");
      if (!templatesNode.isArray()) {
        log.warn("Donor module {} has no zabbix_export.templates", filename);
        return;
      }
      for (JsonNode templateNode : templatesNode) {
        String technicalName = templateNode.path("template").asText("");
        String displayName = templateNode.path("name").asText("");
        List<ZabbixMacroRecord> macros = yamlMapper.readerForListOf(ZabbixMacroRecord.class)
            .readValue(templateNode.path("macros"));
        Map<String, String> compiled = ZabbixTemplateMacroSupport.compileMacros(macros);
        if (compiled.isEmpty()) {
          continue;
        }
        registerAlias(loaded, shortId, compiled);
        if (!technicalName.isBlank()) {
          registerAlias(loaded, technicalName, compiled);
        }
        if (!displayName.isBlank() && !displayName.equals(technicalName)) {
          registerAlias(loaded, displayName, compiled);
        }
        registerAlias(loaded, MODULE_PREFIX + shortId, compiled);
        log.debug("Registered donor macros: id={}, macros={}", shortId, compiled.size());
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to load donor module {}: {}", filename, exception.getMessage());
    }
  }

  private static void registerAlias(Map<String, Map<String, String>> loaded, String ref, Map<String, String> macros) {
    loaded.put(normalizeRef(ref), Map.copyOf(macros));
  }

  static String normalizeRef(String ref) {
    String trimmed = ref.trim();
    if (trimmed.regionMatches(true, 0, MODULE_PREFIX, 0, MODULE_PREFIX.length())) {
      return trimmed.substring(MODULE_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  static String stemToShortId(String stem) {
    if (stem.startsWith("module_")) {
      stem = stem.substring("module_".length());
    }
    return stem.replace('_', '-').toLowerCase(Locale.ROOT);
  }

}
