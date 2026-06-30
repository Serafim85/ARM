package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.MetricDefinition;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateCoverageReportDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDiffSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDiscoveryRuleDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateFeatureSupportDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateItemDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifest;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateManifestEntry;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSource;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateTriggerDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateValueMapDto;
import com.networkscanner.backend.monitoring.dto.PreprocessingFunctionDefinition;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.UnitDefinition;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryConditionRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryFilterRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixExportDocument;
import com.networkscanner.backend.monitoring.dto.ZabbixGraphRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixMacroRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixPreprocessingStep;
import com.networkscanner.backend.monitoring.dto.ZabbixTemplateRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixValueMapRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixValueMapRecord;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.monitoring.util.DonorMacroRegistry;
import com.networkscanner.backend.monitoring.util.TemplateMacroGapInference;
import com.networkscanner.backend.monitoring.util.ZabbixTemplateMacroSupport;
import com.networkscanner.backend.users.repository.AppUserRepository;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class MonitoringTemplateResolverImpl implements MonitoringTemplateResolver {

  private static final Logger log = LoggerFactory.getLogger(MonitoringTemplateResolverImpl.class);

  private static final String MANIFEST_PATH = "classpath:monitoring-templates/manifest.template";
  private static final String DEFAULT_MODULE_MACROS_PATH = "classpath:monitoring-templates/zabbix-module-macros.defaults.json";
  private static final String DEFAULT_MACRO_DONORS = "generic-snmp-macros,vfs-fs-macros,icmp-ping-macros";
  private static final String TEMPLATE_BASE_PATH = "classpath:monitoring-templates/";
  private static final Pattern TEMPLATE_ID_SANITIZER = Pattern.compile("[^a-z0-9-]+");
  private static final Pattern TEMPLATE_CONTEXTUAL_MACRO = Pattern.compile("^\\{\\$([A-Za-z0-9_.]+):.*}$");
  private static final String FILESYSTEM_PACK_VERSION = "filesystem";
  private static final String DEFAULT_SYSTEM_TEMPLATE_DIRS =
      "e:/ZabbixSnmpTemplates/os,e:/ZabbixSnmpTemplates/net,e:/ZabbixSnmpTemplates/db,e:/ZabbixSnmpTemplates/server";
  private final ObjectMapper yamlMapper;
  private final ObjectMapper jsonMapper;
  private final PathMatchingResourcePatternResolver resourceResolver;
  private final UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository;
  private final MonitoringTemplatePriorityOverrideRepository priorityOverrideRepository;
  private final AppUserRepository appUserRepository;
  private final MonitoringTemplateArchiveReader templateArchiveReader;
  private final MonitoringTemplateObfuscator templateObfuscator;
  private Map<String, ResolvedMonitoringTemplate> definitions = Map.of();
  private Set<String> uploadedTemplateIds = Set.of();
  private Map<String, String> uploadedTemplateUsers = Map.of();
  private Map<String, String> uploadedTemplateDisplayUsers = Map.of();
  private Map<String, UploadedTemplateMetadata> uploadedTemplateMetadata = Map.of();
  private String defaultTemplateId = "mib2-default";
  private Map<String, String> defaultModuleMacros = Map.of();
  private final DonorMacroRegistry donorMacroRegistry;

  @Value("${monitoring.system-template-dirs:" + DEFAULT_SYSTEM_TEMPLATE_DIRS + "}")
  private String systemTemplateDirs;

  @Value("${monitoring.system-template-classpath-fallback:true}")
  private boolean classpathFallback;

  @Value("${monitoring.default-macro-donors:" + DEFAULT_MACRO_DONORS + "}")
  private String defaultMacroDonors;

  public MonitoringTemplateResolverImpl(
      ObjectMapper objectMapper,
      UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository,
      MonitoringTemplatePriorityOverrideRepository priorityOverrideRepository,
      AppUserRepository appUserRepository,
      MonitoringTemplateArchiveReader templateArchiveReader,
      MonitoringTemplateObfuscator templateObfuscator
  ) {
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
    this.yamlMapper.findAndRegisterModules();
    this.jsonMapper = new ObjectMapper();
    this.jsonMapper.findAndRegisterModules();
    this.resourceResolver = new PathMatchingResourcePatternResolver();
    this.uploadedMonitoringTemplateRepository = uploadedMonitoringTemplateRepository;
    this.priorityOverrideRepository = priorityOverrideRepository;
    this.appUserRepository = appUserRepository;
    this.templateArchiveReader = templateArchiveReader;
    this.templateObfuscator = templateObfuscator;
    this.defaultModuleMacros = loadDefaultModuleMacros();
    this.donorMacroRegistry = new DonorMacroRegistry(templateObfuscator);
  }

  @Override
  @PostConstruct
  public synchronized void initialize() {
    LoadedTemplates loadedTemplates = loadDefinitions();
    this.defaultTemplateId = loadedTemplates.defaultTemplateId();
    this.definitions = loadedTemplates.templates();
    this.uploadedTemplateIds = loadedTemplates.uploadedTemplateIds();
    this.uploadedTemplateUsers = loadedTemplates.uploadedTemplateUsers();
    this.uploadedTemplateDisplayUsers = loadedTemplates.uploadedTemplateDisplayUsers();
    this.uploadedTemplateMetadata = loadedTemplates.uploadedTemplateMetadata();
  }

  @Override
  public synchronized List<MonitoringTemplateSummaryDto> listTemplates() {
    return definitions.values().stream()
        .filter(ResolvedMonitoringTemplate::uiVisible)
        .map(definition -> new MonitoringTemplateSummaryDto(
            definition.id(),
            definition.type(),
            definition.name(),
            definition.description(),
            resolveUploadedBy(definition.id()),
            resolveUploadedByDisplayName(definition.id()),
            definition.extendsTemplate(),
            definition.vendor(),
            resolveUploadedModel(definition.id()),
            definition.modelRegex(),
            resolveUploadedFirmware(definition.id()),
            definition.priority(),
            definition.schemaVersion(),
            definition.packVersion(),
            definition.templateVersion(),
            uploadedTemplateIds.contains(definition.id()) ? MonitoringTemplateSource.UPLOADED : MonitoringTemplateSource.SYSTEM,
            uploadedTemplateIds.contains(definition.id())
        ))
        .sorted(Comparator.comparing(MonitoringTemplateSummaryDto::id))
        .toList();
  }

  @Override
  public synchronized MonitoringTemplateDetailsDto describeTemplate(String templateId) {
    ResolvedMonitoringTemplate template = resolveTemplateById(templateId);
    return toTemplateDetails(template);
  }

  @Override
  public synchronized MonitoringTemplateImportPreviewDto previewArchive(String originalFilename, byte[] archiveBytes) {
    var uploadedPackage = templateArchiveReader.readSingleTemplatePackage(originalFilename, archiveBytes);
    MonitoringTemplateManifest manifest;
    try {
      manifest = readUploadedManifest(uploadedPackage.manifestYaml());
    } catch (IOException exception) {
      throw new IllegalStateException("Не удалось прочитать manifest.yaml из загруженного архива.", exception);
    }
    MonitoringTemplateManifestEntry entry = manifest.templates().stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("Загруженный шаблон не содержит описания templates."));
    ResolvedMonitoringTemplate rawCandidate;
    try {
      rawCandidate = compileTemplate(manifest, entry, uploadedPackage.templateYaml());
    } catch (IOException exception) {
      throw new IllegalStateException("Не удалось разобрать YAML шаблона из архива.", exception);
    }

    ResolvedMonitoringTemplate effectiveCandidate = isBlank(rawCandidate.extendsTemplate())
        ? rawCandidate
        : mergeTemplates(resolveTemplateById(rawCandidate.extendsTemplate()), rawCandidate);
    ResolvedMonitoringTemplate existing = definitions.get(effectiveCandidate.id());
    return new MonitoringTemplateImportPreviewDto(
        toTemplateDetails(effectiveCandidate),
        buildDiffSummary(existing, effectiveCandidate),
        existing != null
    );
  }

  @Override
  public synchronized ResolvedMonitoringTemplate resolveTemplateById(String templateId) {
    String resolvedId = isBlank(templateId) ? defaultTemplateId : templateId;
    ResolvedMonitoringTemplate template = definitions.get(resolvedId);
    if (template == null) {
      throw new IllegalArgumentException("Шаблон мониторинга не найден: " + resolvedId);
    }
    return template;
  }

  @Override
  public synchronized ResolvedMonitoringTemplate resolveForDevice(
      String selectedTemplateId,
      String vendor,
      String model
  ) {
    return resolveForDevice(selectedTemplateId, vendor, model, null);
  }

  @Override
  public synchronized ResolvedMonitoringTemplate resolveForDevice(
      String selectedTemplateId,
      String vendor,
      String model,
      String firmwareVersion
  ) {
    if (!isBlank(selectedTemplateId)) {
      return resolveTemplateById(selectedTemplateId);
    }

    return definitions.values().stream()
        .filter(ResolvedMonitoringTemplate::uiVisible)
        .filter(definition -> matchesDevice(definition, vendor, model, firmwareVersion))
        .sorted(
            Comparator.<ResolvedMonitoringTemplate>comparingInt(this::specificityScore).reversed()
                .thenComparing(ResolvedMonitoringTemplate::priority, Comparator.reverseOrder())
                .thenComparing(ResolvedMonitoringTemplate::id)
        )
        .findFirst()
        .orElseGet(() -> resolveTemplateById(defaultTemplateId));
  }

  @Override
  public synchronized ResolvedMonitoringTemplate resolveForDevice(
      List<String> selectedTemplateIds,
      String vendor,
      String model
  ) {
    return resolveForDevice(selectedTemplateIds, vendor, model, null);
  }

  @Override
  public synchronized ResolvedMonitoringTemplate resolveForDevice(
      List<String> selectedTemplateIds,
      String vendor,
      String model,
      String firmwareVersion
  ) {
    List<String> normalized = MonitoringTemplateSelectionSupport.normalize(selectedTemplateIds);
    if (!normalized.isEmpty()) {
      return resolveMergedTemplates(normalized);
    }
    return resolveForDevice((String) null, vendor, model, firmwareVersion);
  }

  @Override
  public synchronized ResolvedMonitoringTemplate resolveMergedTemplates(List<String> templateIds) {
    List<String> normalized = MonitoringTemplateSelectionSupport.normalize(templateIds);
    if (normalized.isEmpty()) {
      return resolveTemplateById(defaultTemplateId);
    }
    if (normalized.size() == 1) {
      return resolveTemplateById(normalized.get(0));
    }

    ResolvedMonitoringTemplate first = resolveTemplateById(normalized.get(0));
    Map<String, String> itemSources = new LinkedHashMap<>(first.itemTemplateIds());
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>(first.items());
    Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules = new LinkedHashMap<>(first.discoveryRules());
    Map<String, ZabbixValueMapRuntime> valueMaps = new LinkedHashMap<>(first.valueMaps());
    Map<String, ZabbixTriggerRuntime> triggers = new LinkedHashMap<>(first.triggers());
    Map<String, String> templateMacros = new LinkedHashMap<>(first.templateMacros() == null ? Map.of() : first.templateMacros());
    Map<String, UnitDefinition> units = new LinkedHashMap<>(first.units());
    Map<String, PreprocessingFunctionDefinition> preprocessing = new LinkedHashMap<>(first.preprocessingFunctions());
    Map<String, MetricDefinition> metrics = new LinkedHashMap<>(first.metrics());
    MonitoringTemplateSnmp snmp = first.snmp();
    MonitoringTemplateOids oids = first.oids();
    List<ZabbixGraphRecord> graphs = new ArrayList<>(first.graphs());
    MonitoringTemplateCoverageReportDto coverage = first.coverage();

    for (int i = 1; i < normalized.size(); i++) {
      String nextTemplateId = normalized.get(i);
      ResolvedMonitoringTemplate next = resolveTemplateById(nextTemplateId);
      putIfAbsent(items, next.items());
      putIfAbsent(discoveryRules, next.discoveryRules());
      putIfAbsent(valueMaps, next.valueMaps());
      putIfAbsent(triggers, next.triggers());
      if (next.templateMacros() != null) {
        next.templateMacros().forEach(templateMacros::putIfAbsent);
      }
      putIfAbsent(units, next.units());
      putIfAbsent(preprocessing, next.preprocessingFunctions());
      putIfAbsent(metrics, next.metrics());
      putIfAbsent(itemSources, next.itemTemplateIds());
      oids = mergeOidsFirstWins(oids, next.oids());
      graphs.addAll(next.graphs());
      coverage = mergeCoverageReports(coverage, next.coverage());
      if (snmp == null) {
        snmp = next.snmp();
      }
    }

    Map<String, String> mergedMacros = Map.copyOf(templateMacros);
    return reapplyTemplateMacros(new ResolvedMonitoringTemplate(
        first.id(),
        first.type(),
        first.name(),
        first.description(),
        first.extendsTemplate(),
        first.vendor(),
        first.modelRegex(),
        first.priority(),
        first.schemaVersion(),
        first.packVersion(),
        first.templateVersion(),
        snmp,
        oids,
        Map.copyOf(units),
        Map.copyOf(preprocessing),
        Map.copyOf(metrics),
        Map.copyOf(itemSources),
        Map.copyOf(items),
        Map.copyOf(discoveryRules),
        Map.copyOf(valueMaps),
        Map.copyOf(triggers),
        List.copyOf(graphs),
        mergedMacros,
        coverage,
        first.uiVisible()
    ));
  }

  @Override
  public synchronized String mapValue(String templateId, String valueMapName, String rawValue) {
    if (isBlank(templateId) || isBlank(valueMapName) || rawValue == null) {
      return rawValue;
    }
    ResolvedMonitoringTemplate template = resolveTemplateById(templateId);
    ZabbixValueMapRuntime valueMap = template.valueMaps().get(valueMapName);
    if (valueMap == null || valueMap.mappings() == null) {
      return rawValue;
    }
    return valueMap.mappings().getOrDefault(rawValue, rawValue);
  }

  private <V> void putIfAbsent(Map<String, V> target, Map<String, V> incoming) {
    if (incoming == null || incoming.isEmpty()) {
      return;
    }
    for (Map.Entry<String, V> entry : incoming.entrySet()) {
      target.putIfAbsent(entry.getKey(), entry.getValue());
    }
  }

  private LoadedTemplates loadDefinitions() {
    Map<String, ResolvedMonitoringTemplate> rawTemplates = new LinkedHashMap<>();
    String resolvedDefaultTemplateId = loadSystemTemplates(rawTemplates);

    Set<String> uploadedIds = new HashSet<>();
    Map<String, String> uploadedUsers = new HashMap<>();
    Map<String, String> uploadedDisplayUsers = new HashMap<>();
    Map<String, UploadedTemplateMetadata> uploadedMetadata = new HashMap<>();
    for (UploadedMonitoringTemplateEntity uploaded : uploadedMonitoringTemplateRepository.findAllByOrderByTemplateIdAsc()) {
      String uploadedId = uploaded.getTemplateId();
      try {
        MonitoringTemplateManifest uploadedManifest = readUploadedManifest(uploaded);
        validateManifest(uploadedManifest);
        MonitoringTemplateManifestEntry entry = uploadedManifest.templates().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Загруженный шаблон не содержит описания templates."));
        if (rawTemplates.containsKey(entry.id())) {
          throw new IllegalStateException("Дублируется id шаблона: " + entry.id());
        }
        MonitoringTemplateManifestEntry metadataEntry = overlayUploadedMetadata(entry, uploaded);
        ResolvedMonitoringTemplate compiled = compileTemplate(uploadedManifest, metadataEntry, uploaded.getTemplateYaml());
        rawTemplates.put(entry.id(), compiled);
        uploadedIds.add(entry.id());
        String uploadedBy = uploaded.getUploadedBy();
        // Map.copyOf() doesn't allow null keys/values; tests often omit uploadedBy.
        if (!isBlank(uploadedBy)) {
          uploadedUsers.put(entry.id(), uploadedBy);
          uploadedDisplayUsers.put(entry.id(), resolveDisplayName(uploadedBy));
        }
        uploadedMetadata.put(entry.id(), new UploadedTemplateMetadata(uploaded.getModel(), uploaded.getFirmware()));
      } catch (IOException exception) {
        log.error(
            "Сбой чтения/разбора загруженного шаблона мониторинга: templateId={}, templateFile={}, originalFilename={}, причина: {}",
            uploadedId,
            uploaded.getTemplateFileName(),
            uploaded.getOriginalFilename(),
            exception.getMessage(),
            exception
        );
        throw new IllegalStateException("Не удалось загрузить шаблоны мониторинга.", exception);
      } catch (RuntimeException exception) {
        log.error(
            "Сбой компиляции загруженного шаблона мониторинга: templateId={}, templateFile={}, originalFilename={}, причина: {}",
            uploadedId,
            uploaded.getTemplateFileName(),
            uploaded.getOriginalFilename(),
            exception.getMessage(),
            exception
        );
        throw exception;
      }
    }

    if (!rawTemplates.containsKey(resolvedDefaultTemplateId)) {
      log.error(
          "В манифесте указан defaultTemplateId={}, но такого шаблона нет среди загруженных id: {}",
          resolvedDefaultTemplateId,
          rawTemplates.keySet()
      );
      throw new IllegalStateException("Не найден обязательный шаблон по умолчанию: " + resolvedDefaultTemplateId);
    }

    validateExtendsReferences(rawTemplates);
    validateExtendsCycles(rawTemplates);

    Map<String, ResolvedMonitoringTemplate> effective = new LinkedHashMap<>();
    for (String id : rawTemplates.keySet()) {
      try {
        effective.put(id, resolveEffective(id, rawTemplates, new HashMap<>()));
      } catch (RuntimeException exception) {
        log.error(
            "Сбой разрешения наследования шаблона id={}: {}",
            id,
            exception.getMessage(),
            exception
        );
        throw exception;
      }
    }
    applySystemPriorityOverrides(effective, uploadedIds);
    return new LoadedTemplates(
        resolvedDefaultTemplateId,
        Map.copyOf(effective),
        Set.copyOf(uploadedIds),
        Map.copyOf(uploadedUsers),
        Map.copyOf(uploadedDisplayUsers),
        Map.copyOf(uploadedMetadata)
    );
  }

  private void applySystemPriorityOverrides(
      Map<String, ResolvedMonitoringTemplate> effective,
      Set<String> uploadedIds
  ) {
    for (var override : priorityOverrideRepository.findAll()) {
      String templateId = override.getTemplateId();
      if (uploadedIds.contains(templateId)) {
        continue;
      }
      ResolvedMonitoringTemplate template = effective.get(templateId);
      if (template != null) {
        effective.put(templateId, withPriority(template, override.getPriority()));
      }
    }
  }

  private ResolvedMonitoringTemplate withPriority(ResolvedMonitoringTemplate template, int priority) {
    return new ResolvedMonitoringTemplate(
        template.id(),
        template.type(),
        template.name(),
        template.description(),
        template.extendsTemplate(),
        template.vendor(),
        template.modelRegex(),
        priority,
        template.schemaVersion(),
        template.packVersion(),
        template.templateVersion(),
        template.snmp(),
        template.oids(),
        template.units(),
        template.preprocessingFunctions(),
        template.metrics(),
        template.itemTemplateIds(),
        template.items(),
        template.discoveryRules(),
        template.valueMaps(),
        template.triggers(),
        template.graphs(),
        template.templateMacros(),
        template.coverage(),
        template.uiVisible()
    );
  }

  private MonitoringTemplateManifestEntry overlayUploadedMetadata(
      MonitoringTemplateManifestEntry entry,
      UploadedMonitoringTemplateEntity uploaded
  ) {
    if (entry == null || uploaded == null) {
      return entry;
    }
    String vendor = uploaded.getVendor();
    String modelRegex = uploaded.getModelRegex();
    Integer priority = uploaded.getPriority() != null ? uploaded.getPriority() : entry.priority();
    return new MonitoringTemplateManifestEntry(
        entry.id(),
        entry.file(),
        entry.version(),
        entry.type(),
        entry.snmp(),
        vendor,
        modelRegex,
        priority,
        entry.extendsTemplate(),
        entry.macroDonors(),
        entry.zabbixTemplate(),
        entry.uiVisible()
    );
  }

  private String loadSystemTemplates(Map<String, ResolvedMonitoringTemplate> rawTemplates) {
    String defaultTemplateCandidate = null;
    boolean loadedFromFilesystem = false;
    for (Path directory : parseSystemTemplateDirs()) {
      if (!Files.exists(directory) || !Files.isDirectory(directory)) {
        continue;
      }
      boolean loadedCurrentDirectory = false;

      warnLegacyYamlInDirectory(directory);

      Path manifestPath = directory.resolve("manifest.template");
      Path legacyManifestPath = directory.resolve("manifest.yaml");
      if (Files.exists(legacyManifestPath) && Files.isRegularFile(legacyManifestPath)) {
        log.warn(
            "Пропуск устаревшего manifest.yaml в {} — используйте manifest.template",
            directory
        );
      }
      if (Files.exists(manifestPath) && Files.isRegularFile(manifestPath)) {
        loadedCurrentDirectory = true;
        try {
          String manifestYaml = templateObfuscator.decodeUtf8(Files.readAllBytes(manifestPath));
          MonitoringTemplateManifest manifest = yamlMapper.readValue(manifestYaml, MonitoringTemplateManifest.class);
          validateManifest(manifest);
          defaultTemplateCandidate = mergeManifestTemplates(
              rawTemplates,
              manifest,
              defaultTemplateCandidate,
              fileName -> readFilesystemTemplateYaml(directory, fileName)
          );
        } catch (IOException exception) {
          log.error("Не удалось загрузить manifest.template из {}: {}", manifestPath, exception.getMessage(), exception);
          throw new IllegalStateException("Не удалось загрузить шаблоны мониторинга.", exception);
        }
      }

      if (!Files.exists(manifestPath) || !Files.isRegularFile(manifestPath)) {
        if (loadFilesystemTemplatesRecursive(directory, rawTemplates)) {
          loadedCurrentDirectory = true;
        }
      }
      if (loadedCurrentDirectory) {
        loadedFromFilesystem = true;
      }
    }

    if (classpathFallback || rawTemplates.isEmpty()) {
      defaultTemplateCandidate = loadClasspathTemplates(rawTemplates, defaultTemplateCandidate, loadedFromFilesystem);
    }
    // Prefer wiSLA generic template when available.
    if (rawTemplates.containsKey("network-generic-device-by-snmp")) {
      defaultTemplateCandidate = "network-generic-device-by-snmp";
    }
    if (isBlank(defaultTemplateCandidate) && !rawTemplates.isEmpty()) {
      if (rawTemplates.containsKey("mib2-default")) {
        defaultTemplateCandidate = "mib2-default";
      } else {
        defaultTemplateCandidate = rawTemplates.keySet().iterator().next();
      }
    }
    if (isBlank(defaultTemplateCandidate)) {
      throw new IllegalStateException("Не удалось определить defaultTemplateId для системных шаблонов.");
    }
    return defaultTemplateCandidate;
  }

  private boolean loadFilesystemTemplatesRecursive(
      Path directory,
      Map<String, ResolvedMonitoringTemplate> rawTemplates
  ) {
    List<Path> templateFiles = listTemplateFiles(directory);
    if (templateFiles.isEmpty()) {
      return false;
    }
    boolean loadedAny = false;
    for (Path templatePath : templateFiles) {
      String fileName = templatePath.getFileName().toString();
      if ("manifest.template".equalsIgnoreCase(fileName)
          || "manifest.yaml".equalsIgnoreCase(fileName)
          || "manifest.yml".equalsIgnoreCase(fileName)) {
        continue;
      }
      try {
        String yaml = templateObfuscator.decodeUtf8(Files.readAllBytes(templatePath));
        MonitoringTemplateManifestEntry entry = buildFilesystemEntry(templatePath, yaml);
        MonitoringTemplateManifest syntheticManifest = new MonitoringTemplateManifest(
            "1",
            FILESYSTEM_PACK_VERSION + "-" + LocalDate.now(),
            entry.id(),
            List.of(entry)
        );
        if (rawTemplates.containsKey(entry.id())) {
          log.error("Дублируется id шаблона {} из файла {}", entry.id(), templatePath);
          continue;
        }
        ResolvedMonitoringTemplate compiled = compileTemplate(syntheticManifest, entry, yaml);
        rawTemplates.put(entry.id(), compiled);
        loadedAny = true;
      } catch (IOException | RuntimeException exception) {
        log.error(
            "Пропуск системного шаблона {}: {}",
            templatePath,
            exception.getMessage(),
            exception
        );
      }
    }
    return loadedAny;
  }

  private List<Path> listTemplateFiles(Path directory) {
    try (Stream<Path> walk = Files.walk(directory)) {
      return walk
          .filter(Files::isRegularFile)
          .filter(this::isTemplateFile)
          .sorted()
          .toList();
    } catch (IOException exception) {
      log.error("Не удалось просканировать директорию шаблонов {}: {}", directory, exception.getMessage(), exception);
      throw new IllegalStateException("Не удалось загрузить шаблоны мониторинга.", exception);
    }
  }

  private boolean isTemplateFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".template");
  }

  private boolean isLegacyYamlFile(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".yaml") || name.endsWith(".yml");
  }

  private void warnLegacyYamlInDirectory(Path directory) {
    try (Stream<Path> walk = Files.walk(directory)) {
      walk
          .filter(Files::isRegularFile)
          .filter(this::isLegacyYamlFile)
          .forEach(path -> log.warn(
              "Пропуск устаревшего YAML {} — используйте файл .template",
              path
          ));
    } catch (IOException exception) {
      log.warn("Не удалось проверить устаревшие YAML в {}: {}", directory, exception.getMessage());
    }
  }

  private MonitoringTemplateManifestEntry buildFilesystemEntry(Path templatePath, String templateYaml) throws IOException {
    JsonNode root = readTemplateRoot(templateYaml);
    JsonNode exportNode = root == null ? null : root.path("zabbix_export");
    if (exportNode == null || exportNode.isMissingNode()) {
      throw new IllegalStateException("Файл " + templatePath + " не содержит zabbix_export.");
    }
    JsonNode templatesNode = exportNode.path("templates");
    if (!templatesNode.isArray() || templatesNode.isEmpty()) {
      throw new IllegalStateException("Файл " + templatePath + " не содержит zabbix_export.templates.");
    }
    JsonNode firstTemplate = templatesNode.path(0);
    String technicalName = firstTemplate.path("template").asText("");
    String displayName = firstTemplate.path("name").asText("");
    String sourceName = firstNonBlank(technicalName, firstNonBlank(displayName, templatePath.getFileName().toString()));
    String templateId = sanitizeTemplateId(sourceName);
    String exportVersion = exportNode.path("version").asText("");
    String relativeFile = templatePath.toString().replace('\\', '/');
    return new MonitoringTemplateManifestEntry(
        templateId,
        relativeFile,
        isBlank(exportVersion) ? "1.0.0" : exportVersion,
        "SNMP",
        null,
        null,
        null,
        0,
        null,
        null,
        isBlank(technicalName) ? null : technicalName,
        null
    );
  }

  private String loadClasspathTemplates(
      Map<String, ResolvedMonitoringTemplate> rawTemplates,
      String defaultTemplateIdCandidate,
      boolean skipExisting
  ) {
    MonitoringTemplateManifest manifest;
    try {
      manifest = readManifest();
    } catch (IOException exception) {
      log.error(
          "Не удалось прочитать или разобрать classpath {}: {}",
          MANIFEST_PATH,
          exception.getMessage(),
          exception
      );
      throw new IllegalStateException("Не удалось загрузить шаблоны мониторинга.", exception);
    }
    validateManifest(manifest);
    return mergeManifestTemplates(
        rawTemplates,
        manifest,
        defaultTemplateIdCandidate,
        this::readClasspathTemplateYaml,
        skipExisting
    );
  }

  private String mergeManifestTemplates(
      Map<String, ResolvedMonitoringTemplate> rawTemplates,
      MonitoringTemplateManifest manifest,
      String defaultTemplateIdCandidate,
      TemplateYamlReader yamlReader
  ) {
    return mergeManifestTemplates(rawTemplates, manifest, defaultTemplateIdCandidate, yamlReader, false);
  }

  private String mergeManifestTemplates(
      Map<String, ResolvedMonitoringTemplate> rawTemplates,
      MonitoringTemplateManifest manifest,
      String defaultTemplateIdCandidate,
      TemplateYamlReader yamlReader,
      boolean skipExisting
  ) {
    for (MonitoringTemplateManifestEntry entry : manifest.templates()) {
      if (rawTemplates.containsKey(entry.id())) {
        if (skipExisting) {
          continue;
        }
        throw new IllegalStateException("Дублируется id шаблона: " + entry.id());
      }
      try {
        ResolvedMonitoringTemplate compiled = compileTemplate(manifest, entry, yamlReader.readYaml(entry.file()));
        rawTemplates.put(entry.id(), compiled);
      } catch (IOException | RuntimeException exception) {
        log.error(
            "Пропуск системного шаблона мониторинга: id={}, file={}, причина: {}",
            entry.id(),
            entry.file(),
            exception.getMessage(),
            exception
        );
      }
    }
    if (!isBlank(defaultTemplateIdCandidate)) {
      return defaultTemplateIdCandidate;
    }
    return manifest.defaultTemplateId();
  }

  private List<Path> parseSystemTemplateDirs() {
    if (isBlank(systemTemplateDirs)) {
      return List.of();
    }
    List<Path> parsed = new ArrayList<>();
    Set<String> deduplicated = new HashSet<>();
    for (String token : systemTemplateDirs.split(",")) {
      String trimmed = token == null ? "" : token.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String key = trimmed.replace('\\', '/').toLowerCase();
      if (deduplicated.add(key)) {
        Path resolved = resolveSystemTemplateDir(trimmed);
        parsed.add(resolved);
      }
    }
    return List.copyOf(parsed);
  }

  private Path resolveSystemTemplateDir(String configuredPath) {
    Path path = Path.of(configuredPath);
    if (path.isAbsolute()) {
      return path.normalize();
    }
    Path normalized = path.normalize();
    if (Files.exists(normalized)) {
      return normalized;
    }
    Path parentRelative = Path.of("..").resolve(path).normalize();
    if (Files.exists(parentRelative)) {
      return parentRelative;
    }
    return normalized;
  }

  private MonitoringTemplateManifest readManifest() throws IOException {
    Resource resource = resourceResolver.getResource(MANIFEST_PATH);
    if (!resource.exists()) {
      throw new IllegalStateException("Не найден manifest.template для monitoring templates.");
    }
    try (InputStream inputStream = resource.getInputStream()) {
      String manifestYaml = templateObfuscator.decodeUtf8(inputStream.readAllBytes());
      return yamlMapper.readValue(manifestYaml, MonitoringTemplateManifest.class);
    }
  }

  private MonitoringTemplateManifest readUploadedManifest(UploadedMonitoringTemplateEntity uploaded) throws IOException {
    return yamlMapper.readValue(uploaded.getManifestYaml(), MonitoringTemplateManifest.class);
  }

  private MonitoringTemplateManifest readUploadedManifest(String manifestYaml) throws IOException {
    return yamlMapper.readValue(manifestYaml, MonitoringTemplateManifest.class);
  }

  private String readClasspathTemplateYaml(String fileName) throws IOException {
    String resourcePath = resolveTemplateResourcePath(fileName);
    Resource resource = resourceResolver.getResource(TEMPLATE_BASE_PATH + resourcePath);
    if (!resource.exists()) {
      throw new IllegalStateException("Не найден файл шаблона: " + resourcePath);
    }
    try (InputStream inputStream = resource.getInputStream()) {
      return templateObfuscator.decodeUtf8(inputStream.readAllBytes());
    }
  }

  private String readFilesystemTemplateYaml(Path baseDirectory, String fileName) throws IOException {
    Path root = baseDirectory.normalize();
    Path legacyPath = resolveFilesystemTemplatePath(root, fileName, false);
    Path templatePath = resolveFilesystemTemplatePath(root, fileName, true);
    if (Files.exists(legacyPath) && Files.isRegularFile(legacyPath)
        && (!Files.exists(templatePath) || !Files.isRegularFile(templatePath))) {
      log.warn(
          "Пропуск устаревшего YAML {} — используйте {}",
          legacyPath,
          templatePath.getFileName()
      );
    }
    if (!Files.exists(templatePath) || !Files.isRegularFile(templatePath)) {
      throw new IllegalStateException("Не найден файл шаблона: " + templatePath);
    }
    return templateObfuscator.decodeUtf8(Files.readAllBytes(templatePath));
  }

  static String resolveTemplateResourcePath(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".yaml")) {
      return fileName.substring(0, fileName.length() - 5) + ".template";
    }
    if (lower.endsWith(".yml")) {
      return fileName.substring(0, fileName.length() - 4) + ".template";
    }
    if (lower.endsWith(".template")) {
      return fileName;
    }
    return fileName + ".template";
  }

  private Path resolveFilesystemTemplatePath(Path root, String fileName, boolean obfuscated) {
    String resolvedName = obfuscated ? resolveTemplateResourcePath(fileName) : fileName;
    Path templatePath = Path.of(resolvedName);
    if (!templatePath.isAbsolute()) {
      templatePath = root.resolve(resolvedName);
    }
    templatePath = templatePath.normalize();
    if (!templatePath.startsWith(root)) {
      throw new IllegalStateException("Недопустимый путь шаблона вне директории: " + fileName);
    }
    return templatePath;
  }

  private String sanitizeTemplateId(String source) {
    String base = source == null ? "" : source.trim().toLowerCase();
    base = base.replace('_', '-');
    String sanitized = TEMPLATE_ID_SANITIZER.matcher(base).replaceAll("-");
    sanitized = sanitized.replaceAll("-{2,}", "-");
    sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
    return sanitized.isBlank() ? "zabbix-template" : sanitized;
  }

  private void validateManifest(MonitoringTemplateManifest manifest) {
    if (manifest == null || isBlank(manifest.schemaVersion()) || isBlank(manifest.packVersion())) {
      throw new IllegalStateException("manifest.yaml должен содержать schemaVersion и packVersion.");
    }
    if (isBlank(manifest.defaultTemplateId())) {
      throw new IllegalStateException("manifest.yaml должен содержать defaultTemplateId.");
    }
    if (manifest.templates() == null || manifest.templates().isEmpty()) {
      throw new IllegalStateException("manifest.yaml не содержит описаний шаблонов.");
    }
    for (MonitoringTemplateManifestEntry entry : manifest.templates()) {
      if (entry == null || isBlank(entry.id()) || isBlank(entry.file())) {
        throw new IllegalStateException("manifest.yaml содержит шаблон без id или file.");
      }
      if (!isBlank(entry.modelRegex())) {
        Pattern.compile(entry.modelRegex(), Pattern.CASE_INSENSITIVE);
      }
    }
  }

  private ResolvedMonitoringTemplate compileTemplate(
      MonitoringTemplateManifest manifest,
      MonitoringTemplateManifestEntry entry,
      String templateYaml
  ) throws IOException {
    ZabbixExportDocument.ZabbixExportPayload exportPayload;
    JsonNode root = readTemplateRoot(templateYaml);
    JsonNode exportNode = root == null ? null : root.get("zabbix_export");
    if (exportNode == null || exportNode.isMissingNode()) {
      throw new IllegalStateException("Файл " + entry.file() + " не содержит zabbix_export.");
    }
    exportPayload = yamlMapper.treeToValue(exportNode, ZabbixExportDocument.ZabbixExportPayload.class);
    if (exportPayload == null || exportPayload.templates() == null || exportPayload.templates().isEmpty()) {
      throw new IllegalStateException("Файл " + entry.file() + " не содержит zabbix_export.templates.");
    }

    ZabbixTemplateRecord zabbixTemplate = selectTemplate(exportPayload.templates(), entry);
    JsonNode templateNode = selectTemplateNode(exportNode, entry);
    Map<String, String> templateMacros = buildTemplateMacroCatalog(entry, zabbixTemplate, exportPayload.templates());
    Map<String, ZabbixValueMapRuntime> valueMaps = compileValueMaps(zabbixTemplate.valuemaps());
    Map<String, ZabbixItemRuntime> items = compileItems(zabbixTemplate.items(), false, null);
    Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules = compileDiscoveryRules(zabbixTemplate.discoveryRules(), templateMacros);
    Map<String, ZabbixTriggerRuntime> triggers = compileTriggers(
        zabbixTemplate.items(),
        zabbixTemplate.discoveryRules(),
        templateMacros
    );

    DerivedTemplateViews views = deriveViews(items, discoveryRules);
    MonitoringTemplateCoverageReportDto coverage = buildCoverageReport(exportNode, templateNode, items, discoveryRules);

    return new ResolvedMonitoringTemplate(
        entry.id(),
        firstNonBlank(entry.type(), "SNMP"),
        firstNonBlank(zabbixTemplate.name(), firstNonBlank(zabbixTemplate.template(), entry.id())),
        firstNonBlank(zabbixTemplate.description(), ""),
        entry.extendsTemplate(),
        entry.vendor(),
        entry.modelRegex(),
        entry.priority() == null ? 0 : entry.priority(),
        manifest.schemaVersion(),
        manifest.packVersion(),
        firstNonBlank(entry.version(), exportPayload.version()),
        mergeSnmp(defaultSnmp(), entry.snmp()),
        views.oids(),
        views.units(),
        views.preprocessingFunctions(),
        views.metrics(),
        mapItemSources(items, entry.id()),
        Map.copyOf(items),
        Map.copyOf(discoveryRules),
        Map.copyOf(valueMaps),
        Map.copyOf(triggers),
        zabbixTemplate.graphs() == null ? List.of() : List.copyOf(zabbixTemplate.graphs()),
        Map.copyOf(templateMacros),
        coverage,
        resolveUiVisible(entry)
    );
  }

  private Map<String, String> buildTemplateMacroCatalog(
      MonitoringTemplateManifestEntry entry,
      ZabbixTemplateRecord template,
      List<ZabbixTemplateRecord> exportTemplates
  ) {
    Map<String, String> catalog = new LinkedHashMap<>(defaultModuleMacros);
    if (isSnmpTemplateType(entry.type())) {
      applyDonorReferences(parseDonorRefs(defaultMacroDonors), catalog, exportTemplates);
    }
    catalog.putAll(ZabbixTemplateMacroSupport.collectLinkedTemplateMacros(template, exportTemplates));
    applyDonorReferences(entry.macroDonors(), catalog, exportTemplates);

    Map<String, String> beforeOwnMacros = Map.copyOf(catalog);
    List<String> inferredDonors = TemplateMacroGapInference.inferDonorIds(template, beforeOwnMacros);
    if (!inferredDonors.isEmpty()) {
      log.info("Template {}: auto-attached macro donors {}", entry.id(), inferredDonors);
      applyDonorReferences(inferredDonors, catalog, exportTemplates);
    }

    catalog.putAll(ZabbixTemplateMacroSupport.compileMacros(template.macros()));
    return Map.copyOf(catalog);
  }

  private void applyDonorReferences(
      List<String> donorRefs,
      Map<String, String> catalog,
      List<ZabbixTemplateRecord> exportTemplates
  ) {
    if (donorRefs == null || donorRefs.isEmpty()) {
      return;
    }
    for (String donorRef : donorRefs) {
      if (isBlank(donorRef)) {
        continue;
      }
      String trimmed = donorRef.trim();
      ZabbixTemplateRecord donorInExport = findTemplateInExport(exportTemplates, trimmed);
      if (donorInExport != null) {
        catalog.putAll(ZabbixTemplateMacroSupport.compileMacros(donorInExport.macros()));
        continue;
      }
      Map<String, String> donorMacros = donorMacroRegistry.resolve(trimmed);
      if (!donorMacros.isEmpty()) {
        catalog.putAll(donorMacros);
      } else {
        log.debug("Macro donor not found in export or registry: {}", trimmed);
      }
    }
  }

  private List<String> parseDonorRefs(String csv) {
    if (isBlank(csv)) {
      return List.of();
    }
    return Stream.of(csv.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
  }

  private boolean isSnmpTemplateType(String type) {
    return type == null || type.isBlank() || "SNMP".equalsIgnoreCase(type.trim());
  }

  private ZabbixTemplateRecord findTemplateInExport(List<ZabbixTemplateRecord> templates, String name) {
    if (templates == null || isBlank(name)) {
      return null;
    }
    for (ZabbixTemplateRecord candidate : templates) {
      if (candidate == null) {
        continue;
      }
      if (name.equals(candidate.template()) || name.equals(candidate.name())) {
        return candidate;
      }
    }
    return null;
  }

  private Map<String, String> loadDefaultModuleMacros() {
    try {
      Resource resource = resourceResolver.getResource(DEFAULT_MODULE_MACROS_PATH);
      if (!resource.exists()) {
        log.warn("Default Zabbix module macros resource not found: {}", DEFAULT_MODULE_MACROS_PATH);
        return Map.of();
      }
      try (InputStream inputStream = resource.getInputStream()) {
        Map<String, String> loaded = jsonMapper.readValue(inputStream, jsonMapper.getTypeFactory()
            .constructMapType(LinkedHashMap.class, String.class, String.class));
        return loaded == null ? Map.of() : Map.copyOf(loaded);
      }
    } catch (IOException exception) {
      log.warn("Failed to load default Zabbix module macros: {}", exception.getMessage());
      return Map.of();
    }
  }

  private ResolvedMonitoringTemplate reapplyTemplateMacros(ResolvedMonitoringTemplate template) {
    Map<String, String> macros = template.templateMacros() == null ? Map.of() : template.templateMacros();
    if (macros.isEmpty()) {
      return template;
    }
    return new ResolvedMonitoringTemplate(
        template.id(),
        template.type(),
        template.name(),
        template.description(),
        template.extendsTemplate(),
        template.vendor(),
        template.modelRegex(),
        template.priority(),
        template.schemaVersion(),
        template.packVersion(),
        template.templateVersion(),
        template.snmp(),
        template.oids(),
        template.units(),
        template.preprocessingFunctions(),
        template.metrics(),
        template.itemTemplateIds(),
        template.items(),
        ZabbixTemplateMacroSupport.resolveDiscoveryRules(template.discoveryRules(), macros),
        template.valueMaps(),
        ZabbixTemplateMacroSupport.resolveTriggers(template.triggers(), macros),
        template.graphs(),
        macros,
        template.coverage(),
        template.uiVisible()
    );
  }

  private static boolean resolveUiVisible(MonitoringTemplateManifestEntry entry) {
    return entry.uiVisible() == null || entry.uiVisible();
  }

  private ZabbixTemplateRecord selectTemplate(List<ZabbixTemplateRecord> templates, MonitoringTemplateManifestEntry entry) {
    if (isBlank(entry.zabbixTemplate())) {
      return templates.get(0);
    }
    return templates.stream()
        .filter(template -> entry.zabbixTemplate().equals(template.template())
            || entry.zabbixTemplate().equals(template.name()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "В файле " + entry.file() + " не найден шаблон Zabbix: " + entry.zabbixTemplate()));
  }

  private Map<String, ZabbixValueMapRuntime> compileValueMaps(List<ZabbixValueMapRecord> valueMaps) {
    Map<String, ZabbixValueMapRuntime> compiled = new LinkedHashMap<>();
    if (valueMaps == null) {
      return compiled;
    }
    for (ZabbixValueMapRecord valueMap : valueMaps) {
      Map<String, String> mappings = valueMap.mappings() == null ? Map.of() : valueMap.mappings().stream()
          .filter(mapping -> !isBlank(mapping.value()))
          .collect(Collectors.toMap(
              mapping -> mapping.value(),
              mapping -> firstNonBlank(mapping.newvalue(), mapping.value()),
              (left, right) -> right,
              LinkedHashMap::new
          ));
      compiled.put(valueMap.name(), new ZabbixValueMapRuntime(valueMap.uuid(), valueMap.name(), Map.copyOf(mappings)));
    }
    return compiled;
  }

  private Map<String, ZabbixItemRuntime> compileItems(
      List<ZabbixItemRecord> items,
      boolean discoveryPrototype,
      String discoveryRuleKey
  ) {
    Map<String, ZabbixItemRuntime> compiled = new LinkedHashMap<>();
    if (items == null) {
      return compiled;
    }
    for (ZabbixItemRecord item : items) {
      if (isBlank(item.key())) {
        continue;
      }
      if (!isBlank(item.snmpOid()) && item.snmpOid().trim().startsWith("discovery[")) {
        continue;
      }
      String normalizedType = firstNonBlank(item.type(), "SNMP_AGENT");
      String normalizedSnmpOid = item.snmpOid() == null ? null : normalizeSnmpOid(item.snmpOid());
      if (isSnmpType(normalizedType) && isBlank(normalizedSnmpOid) && !isIcmpSimpleItem(item.key(), normalizedType)) {
        continue;
      }
      compiled.put(item.key(), new ZabbixItemRuntime(
          item.uuid(),
          item.key(),
          firstNonBlank(item.name(), item.key()),
          normalizedType,
          normalizedSnmpOid,
          parseDelaySeconds(item.delay(), 60),
          firstNonBlank(item.valueType(), "FLOAT"),
          firstNonBlank(item.units(), ""),
          firstNonBlank(item.params(), ""),
          item.masterItem() == null ? null : item.masterItem().key(),
          firstNonBlank(item.url(), ""),
          firstNonBlank(item.description(), ""),
          item.preprocessing() == null ? List.of() : List.copyOf(item.preprocessing()),
          item.valuemap() == null ? null : item.valuemap().name(),
          discoveryPrototype,
          discoveryRuleKey
      ));
    }
    return compiled;
  }

  private boolean isSnmpType(String type) {
    return type == null || type.isBlank()
        || "SNMP_AGENT".equalsIgnoreCase(type)
        || "SIMPLE".equalsIgnoreCase(type);
  }

  private boolean isIcmpSimpleItem(String itemKey, String itemType) {
    if (!"SIMPLE".equalsIgnoreCase(itemType)) {
      return false;
    }
    String baseKey = normalizeItemKeyBase(itemKey);
    return "icmpping".equals(baseKey) || "icmppingloss".equals(baseKey) || "icmppingsec".equals(baseKey);
  }

  private String normalizeItemKeyBase(String itemKey) {
    if (itemKey == null) {
      return "";
    }
    int bracketIndex = itemKey.indexOf('[');
    String baseKey = bracketIndex >= 0 ? itemKey.substring(0, bracketIndex) : itemKey;
    return baseKey.trim().toLowerCase();
  }

  private Map<String, String> compileTemplateMacros(List<ZabbixMacroRecord> macros) {
    Map<String, String> compiled = new LinkedHashMap<>();
    if (macros == null) {
      return compiled;
    }
    for (ZabbixMacroRecord macro : macros) {
      if (macro == null || isBlank(macro.macro()) || macro.value() == null) {
        continue;
      }
      compiled.put(macro.macro(), macro.value());
    }
    return Map.copyOf(compiled);
  }

  private Map<String, ZabbixDiscoveryRuleRuntime> compileDiscoveryRules(
      List<ZabbixDiscoveryRuleRecord> discoveryRules,
      Map<String, String> templateMacros
  ) {
    Map<String, ZabbixDiscoveryRuleRuntime> compiled = new LinkedHashMap<>();
    if (discoveryRules == null) {
      return compiled;
    }
    for (ZabbixDiscoveryRuleRecord discoveryRule : discoveryRules) {
      compiled.put(discoveryRule.key(), new ZabbixDiscoveryRuleRuntime(
          discoveryRule.uuid(),
          discoveryRule.key(),
          firstNonBlank(discoveryRule.name(), discoveryRule.key()),
          firstNonBlank(discoveryRule.type(), "SNMP_AGENT"),
          discoveryRule.snmpOid(),
          discoveryRule.masterItem() == null ? null : discoveryRule.masterItem().key(),
          discoveryRule.preprocessing() == null ? List.of() : List.copyOf(discoveryRule.preprocessing()),
          discoveryRule.lldMacroPaths() == null ? List.of() : List.copyOf(discoveryRule.lldMacroPaths()),
          parseDelaySeconds(discoveryRule.delay(), 3600),
          parseLifetimeSeconds(discoveryRule.lifetime(), 86400),
          compileDiscoveryFilter(discoveryRule.filter(), templateMacros),
          List.copyOf(compileItems(discoveryRule.itemPrototypes(), true, discoveryRule.key()).values()),
          compileTriggerList(discoveryRule.triggerPrototypes(), true, discoveryRule.key(), templateMacros),
          discoveryRule.graphPrototypes() == null ? List.of() : List.copyOf(discoveryRule.graphPrototypes())
      ));
    }
    return compiled;
  }

  private ZabbixDiscoveryFilterRecord compileDiscoveryFilter(
      ZabbixDiscoveryFilterRecord filter,
      Map<String, String> templateMacros
  ) {
    if (filter == null || filter.conditions() == null || filter.conditions().isEmpty()) {
      return null;
    }
    List<ZabbixDiscoveryConditionRecord> compiledConditions = filter.conditions().stream()
        .filter(condition -> condition != null && !isBlank(condition.macro()))
        .map(condition -> new ZabbixDiscoveryConditionRecord(
            condition.macro(),
            ZabbixTemplateMacroSupport.applyTemplateMacros(condition.value(), templateMacros),
            condition.operator()
        ))
        .toList();
    if (compiledConditions.isEmpty()) {
      return null;
    }
    return new ZabbixDiscoveryFilterRecord(firstNonBlank(filter.evaltype(), "AND"), List.copyOf(compiledConditions));
  }

  private Map<String, ZabbixTriggerRuntime> compileTriggers(
      List<ZabbixItemRecord> items,
      List<ZabbixDiscoveryRuleRecord> discoveryRules,
      Map<String, String> templateMacros
  ) {
    Map<String, ZabbixTriggerRuntime> compiled = new LinkedHashMap<>();
    if (items != null) {
      for (ZabbixItemRecord item : items) {
        for (ZabbixTriggerRuntime trigger : compileTriggerList(item.triggers(), false, null, templateMacros)) {
          compiled.put(firstNonBlank(trigger.uuid(), trigger.expression()), trigger);
        }
      }
    }
    if (discoveryRules != null) {
      for (ZabbixDiscoveryRuleRecord discoveryRule : discoveryRules) {
        for (ZabbixTriggerRuntime trigger : compileTriggerList(
            discoveryRule.triggerPrototypes(), true, discoveryRule.key(), templateMacros)) {
          compiled.put(firstNonBlank(trigger.uuid(), trigger.expression()), trigger);
        }
      }
    }
    return compiled;
  }

  private List<ZabbixTriggerRuntime> compileTriggerList(
      List<ZabbixTriggerRecord> triggers,
      boolean discoveryPrototype,
      String discoveryRuleKey,
      Map<String, String> templateMacros
  ) {
    if (triggers == null) {
      return List.of();
    }
    List<ZabbixTriggerRuntime> compiled = new ArrayList<>();
    for (ZabbixTriggerRecord trigger : triggers) {
      if (isBlank(trigger.expression())) {
        continue;
      }
      String resolvedExpression = ZabbixTemplateMacroSupport.applyTemplateMacros(trigger.expression(), templateMacros);
      if (ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(resolvedExpression)) {
        log.warn(
            "Skip trigger '{}' due to unresolved template macros in expression: {}",
            firstNonBlank(trigger.name(), trigger.uuid()),
            resolvedExpression
        );
        continue;
      }
      String resolvedRecoveryExpression =
          isBlank(trigger.recoveryExpression()) ? null
              : ZabbixTemplateMacroSupport.applyTemplateMacros(trigger.recoveryExpression(), templateMacros);
      if (ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(resolvedRecoveryExpression)) {
        log.warn(
            "Trigger '{}' has unresolved macros in recovery expression, fallback to implicit recovery: {}",
            firstNonBlank(trigger.name(), trigger.uuid()),
            resolvedRecoveryExpression
        );
        resolvedRecoveryExpression = null;
      }
      compiled.add(new ZabbixTriggerRuntime(
          trigger.uuid(),
          firstNonBlank(trigger.name(), trigger.expression()),
          resolvedExpression,
          firstNonBlank(trigger.recoveryMode(), "EXPRESSION"),
          resolvedRecoveryExpression,
          trigger.dependencies() == null ? List.of() : trigger.dependencies().stream()
              .map(this::dependencyKeyFromRecord)
              .filter(value -> value != null && !value.isBlank())
              .toList(),
          trigger.tags() == null ? List.of() : List.copyOf(trigger.tags()),
          isEnabledFlag(trigger.manualClose()),
          firstNonBlank(trigger.priority(), "WARNING"),
          discoveryPrototype,
          discoveryRuleKey
      ));
    }
    return List.copyOf(compiled);
  }

  private String applyTemplateMacros(String value, Map<String, String> templateMacros) {
    if (value == null || value.isBlank() || templateMacros == null || templateMacros.isEmpty()) {
      return value;
    }
    List<TemplateMacroSpan> references = findTemplateMacroReferences(value);
    if (references.isEmpty()) {
      return value;
    }
    StringBuilder resolved = new StringBuilder();
    int cursor = 0;
    for (TemplateMacroSpan reference : references) {
      resolved.append(value, cursor, reference.startIndex());
      String macroReference = reference.value();
      String replacement = resolveTemplateMacroValue(macroReference, templateMacros);
      resolved.append(replacement == null ? macroReference : replacement);
      cursor = reference.endIndex();
    }
    resolved.append(value.substring(cursor));
    return resolved.toString();
  }

  private boolean containsUnresolvedTemplateMacroReference(String value) {
    return ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(value);
  }

  private String resolveTemplateMacroValue(String macroReference, Map<String, String> templateMacros) {
    if (macroReference == null || macroReference.isBlank()) {
      return null;
    }
    String exactValue = templateMacros.get(macroReference);
    if (exactValue != null) {
      return exactValue;
    }
    String baseMacro = contextualMacroBase(macroReference);
    if (baseMacro == null) {
      log.debug("Template macro unresolved macro_base_missing: {}", macroReference);
      return null;
    }
    String fallbackValue = templateMacros.get(baseMacro);
    if (fallbackValue == null) {
      log.debug("Template macro unresolved macro_base_missing: contextual={}, base={}", macroReference, baseMacro);
      return null;
    }
    log.debug("Template macro resolved macro_context_missing: contextual={}, base={}", macroReference, baseMacro);
    return fallbackValue;
  }

  private String contextualMacroBase(String macroReference) {
    Matcher matcher = TEMPLATE_CONTEXTUAL_MACRO.matcher(macroReference);
    if (!matcher.matches()) {
      return null;
    }
    return "{$" + matcher.group(1) + "}";
  }

  private List<TemplateMacroSpan> findTemplateMacroReferences(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    List<TemplateMacroSpan> spans = new ArrayList<>();
    for (int i = 0; i < value.length() - 1; i++) {
      if (value.charAt(i) != '{' || value.charAt(i + 1) != '$') {
        continue;
      }
      int end = findMacroReferenceEnd(value, i);
      if (end < 0) {
        break;
      }
      spans.add(new TemplateMacroSpan(i, end + 1, value.substring(i, end + 1)));
      i = end;
    }
    return spans;
  }

  private int findMacroReferenceEnd(String value, int startIndex) {
    int depth = 1;
    boolean quoted = false;
    for (int i = startIndex + 2; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
        quoted = !quoted;
      }
      if (quoted) {
        continue;
      }
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private record TemplateMacroSpan(
      int startIndex,
      int endIndex,
      String value
  ) {
  }

  private DerivedTemplateViews deriveViews(
      Map<String, ZabbixItemRuntime> items,
      Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules
  ) {
    Map<String, String> discoveryOids = new LinkedHashMap<>();
    Map<String, String> detailsOids = new LinkedHashMap<>();
    Map<String, String> interfaceOids = new LinkedHashMap<>();
    Map<String, UnitDefinition> units = new LinkedHashMap<>();
    Map<String, PreprocessingFunctionDefinition> preprocessingFunctions = new LinkedHashMap<>();
    Map<String, MetricDefinition> metrics = new LinkedHashMap<>();

    for (ZabbixItemRuntime item : items.values()) {
      registerKnownOid(detailsOids, item);
      registerKnownOid(discoveryOids, item);
      registerUnit(units, item.units());
      registerPreprocessing(preprocessingFunctions, item.preprocessing());
      registerMetric(metrics, item);
    }

    for (ZabbixDiscoveryRuleRuntime discoveryRule : discoveryRules.values()) {
      for (ZabbixItemRuntime item : discoveryRule.itemPrototypes()) {
        registerUnit(units, item.units());
        registerPreprocessing(preprocessingFunctions, item.preprocessing());
        registerMetric(metrics, item);
        registerInterfaceOid(interfaceOids, item);
      }
    }

    discoveryOids.putIfAbsent("sysName", "1.3.6.1.2.1.1.5.0");
    discoveryOids.putIfAbsent("sysDescr", "1.3.6.1.2.1.1.1.0");
    discoveryOids.putIfAbsent("sysObjectId", "1.3.6.1.2.1.1.2.0");
    discoveryOids.putIfAbsent("macAddress", "1.3.6.1.2.1.17.1.1.0");
    discoveryOids.putIfAbsent("serialNumber", "1.3.6.1.2.1.47.1.1.1.1.11.1");
    discoveryOids.putIfAbsent("model", "1.3.6.1.2.1.47.1.1.1.1.13.1");
    detailsOids.putIfAbsent("sysDescr", discoveryOids.get("sysDescr"));
    detailsOids.putIfAbsent("uptime", "1.3.6.1.2.1.1.3.0");
    detailsOids.putIfAbsent("adminContact", "1.3.6.1.2.1.1.4.0");
    detailsOids.putIfAbsent("location", "1.3.6.1.2.1.1.6.0");
    detailsOids.putIfAbsent("hardwareVersion", "1.3.6.1.2.1.47.1.1.1.1.8.1");
    detailsOids.putIfAbsent("bootVersion", "1.3.6.1.2.1.47.1.1.1.1.10.1");

    return new DerivedTemplateViews(
        new MonitoringTemplateOids(Map.copyOf(discoveryOids), Map.copyOf(detailsOids), Map.copyOf(interfaceOids)),
        Map.copyOf(units),
        Map.copyOf(preprocessingFunctions),
        Map.copyOf(metrics)
    );
  }

  private void registerKnownOid(Map<String, String> target, ZabbixItemRuntime item) {
    String key = item.key();
    if ("sysName".equals(key)) {
      target.put("sysName", item.snmpOid());
    } else if ("sysDescr".equals(key)) {
      target.put("sysDescr", item.snmpOid());
    } else if ("sysContact".equals(key)) {
      target.put("adminContact", item.snmpOid());
    } else if ("sysLocation".equals(key)) {
      target.put("location", item.snmpOid());
    } else if ("sysUpTime".equals(key)) {
      target.put("uptime", item.snmpOid());
    } else if ("entPhysicalSerialNum".equals(key)) {
      target.put("serialNumber", item.snmpOid());
    } else if ("entPhysicalModelName".equals(key) || "entPhysicalDescr".equals(key)) {
      target.put("model", item.snmpOid());
    } else if ("entPhysicalHardwareRev".equals(key)) {
      target.put("hardwareVersion", item.snmpOid());
    } else if ("entPhysicalFirmwareRev".equals(key) || "entPhysicalSoftwareRev".equals(key)) {
      target.put("bootVersion", item.snmpOid());
    }
  }

  private void registerInterfaceOid(Map<String, String> target, ZabbixItemRuntime item) {
    if (item == null || isBlank(item.snmpOid())) {
      return;
    }
    String baseOid = item.snmpOid().replace(".{#SNMPINDEX}", "").replace(".{#SNMPVALUE}", "");
    String key = item.key();
    if (key.startsWith("ifName[")) {
      target.put("ifName", baseOid);
    } else if (key.startsWith("ifDescr[")) {
      target.put("ifDescr", baseOid);
    } else if (key.startsWith("ifAlias[")) {
      target.put("ifAlias", baseOid);
    } else if (key.startsWith("ifAdminStatus[")) {
      target.put("ifAdminStatus", baseOid);
    } else if (key.startsWith("ifOperStatus[")) {
      target.put("ifOperStatus", baseOid);
    } else if (key.startsWith("ifHighSpeed[")) {
      target.put("ifSpeed", baseOid);
    }
  }

  private void registerUnit(Map<String, UnitDefinition> units, String unitValue) {
    if (isBlank(unitValue)) {
      return;
    }
    units.putIfAbsent(unitValue, new UnitDefinition(unitValue, unitValue));
  }

  private void registerPreprocessing(
      Map<String, PreprocessingFunctionDefinition> functions,
      List<ZabbixPreprocessingStep> preprocessing
  ) {
    if (preprocessing == null) {
      return;
    }
    for (ZabbixPreprocessingStep step : preprocessing) {
      if (step == null || isBlank(step.type())) {
        continue;
      }
      String name = step.type();
      String logic = step.parameters() == null ? "" : String.join(", ", step.parameters());
      functions.putIfAbsent(name, new PreprocessingFunctionDefinition(name, logic));
    }
  }

  private void registerMetric(Map<String, MetricDefinition> metrics, ZabbixItemRuntime item) {
    if (item == null || item.isTextual() || isBlank(item.key())) {
      return;
    }
    String unit = isBlank(item.units()) ? "value" : item.units();
    String itemDisplayName = isBlank(item.name()) ? null : item.name().trim();
    metrics.putIfAbsent(
        item.key(),
        new MetricDefinition(item.snmpOid(), unit, null, null, summarizePreprocessing(item), itemDisplayName));
  }

  private String summarizePreprocessing(ZabbixItemRuntime item) {
    if (item.preprocessing() == null || item.preprocessing().isEmpty()) {
      return null;
    }
    return item.preprocessing().stream()
        .map(step -> step.type())
        .collect(Collectors.joining(" -> "));
  }

  private void validateExtendsReferences(Map<String, ResolvedMonitoringTemplate> loaded) {
    for (ResolvedMonitoringTemplate definition : loaded.values()) {
      if (!isBlank(definition.extendsTemplate()) && !loaded.containsKey(definition.extendsTemplate())) {
        throw new IllegalStateException(
            "В шаблоне " + definition.id() + " указан неизвестный parent: " + definition.extendsTemplate());
      }
    }
  }

  private void validateExtendsCycles(Map<String, ResolvedMonitoringTemplate> loaded) {
    Set<String> visited = new HashSet<>();
    Set<String> inStack = new HashSet<>();
    for (String id : loaded.keySet()) {
      detectCycle(id, loaded, visited, inStack, new ArrayDeque<>());
    }
  }

  private void detectCycle(
      String id,
      Map<String, ResolvedMonitoringTemplate> loaded,
      Set<String> visited,
      Set<String> inStack,
      Deque<String> trace
  ) {
    if (visited.contains(id)) {
      return;
    }
    if (inStack.contains(id)) {
      throw new IllegalStateException("Обнаружен цикл наследования шаблонов: " + String.join(" -> ", trace));
    }
    inStack.add(id);
    trace.addLast(id);
    ResolvedMonitoringTemplate definition = loaded.get(id);
    if (definition != null && !isBlank(definition.extendsTemplate())) {
      detectCycle(definition.extendsTemplate(), loaded, visited, inStack, trace);
    }
    trace.removeLast();
    inStack.remove(id);
    visited.add(id);
  }

  private ResolvedMonitoringTemplate resolveEffective(
      String templateId,
      Map<String, ResolvedMonitoringTemplate> rawTemplates,
      Map<String, ResolvedMonitoringTemplate> cache
  ) {
    if (cache.containsKey(templateId)) {
      return cache.get(templateId);
    }
    ResolvedMonitoringTemplate definition = rawTemplates.get(templateId);
    if (definition == null) {
      throw new IllegalStateException("Шаблон не найден: " + templateId);
    }

    ResolvedMonitoringTemplate parent = isBlank(definition.extendsTemplate())
        ? emptyTemplate(defaultTemplateId)
        : resolveEffective(definition.extendsTemplate(), rawTemplates, cache);

    ResolvedMonitoringTemplate merged = mergeTemplates(parent, definition);
    cache.put(templateId, merged);
    return merged;
  }

  private ResolvedMonitoringTemplate mergeTemplates(ResolvedMonitoringTemplate parent, ResolvedMonitoringTemplate child) {
    Map<String, ZabbixItemRuntime> mergedItems = mergeTypedMap(parent.items(), child.items());
    Map<String, ZabbixDiscoveryRuleRuntime> mergedDiscoveryRules = mergeDiscoveryRules(parent.discoveryRules(), child.discoveryRules());
    Map<String, ZabbixValueMapRuntime> mergedValueMaps = mergeTypedMap(parent.valueMaps(), child.valueMaps());
    Map<String, String> mergedMacros = ZabbixTemplateMacroSupport.mergeMacroMaps(
        parent.templateMacros(),
        child.templateMacros()
    );
    Map<String, ZabbixTriggerRuntime> mergedTriggers = mergeTypedMap(parent.triggers(), child.triggers());
    List<ZabbixGraphRecord> mergedGraphs = new ArrayList<>(parent.graphs());
    mergedGraphs.addAll(child.graphs());

    DerivedTemplateViews views = deriveViews(mergedItems, mergedDiscoveryRules);
    return reapplyTemplateMacros(new ResolvedMonitoringTemplate(
        child.id(),
        firstNonBlank(child.type(), parent.type()),
        firstNonBlank(child.name(), child.id()),
        firstNonBlank(child.description(), parent.description()),
        child.extendsTemplate(),
        firstNonBlank(child.vendor(), parent.vendor()),
        firstNonBlank(child.modelRegex(), parent.modelRegex()),
        child.priority() == 0 ? parent.priority() : child.priority(),
        firstNonBlank(child.schemaVersion(), parent.schemaVersion()),
        firstNonBlank(child.packVersion(), parent.packVersion()),
        firstNonBlank(child.templateVersion(), parent.templateVersion()),
        mergeSnmp(parent.snmp(), child.snmp()),
        mergeOids(parent.oids(), views.oids()),
        mergeTypedMap(parent.units(), views.units()),
        mergeTypedMap(parent.preprocessingFunctions(), views.preprocessingFunctions()),
        mergeTypedMap(parent.metrics(), views.metrics()),
        mapItemSources(mergedItems, child.id()),
        Map.copyOf(mergedItems),
        Map.copyOf(mergedDiscoveryRules),
        Map.copyOf(mergedValueMaps),
        Map.copyOf(mergedTriggers),
        List.copyOf(mergedGraphs),
        mergedMacros,
        mergeCoverageReports(parent.coverage(), child.coverage()),
        child.uiVisible()
    ));
  }

  private MonitoringTemplateSnmp mergeSnmp(MonitoringTemplateSnmp parent, MonitoringTemplateSnmp child) {
    if (child == null) {
      return parent;
    }
    return new MonitoringTemplateSnmp(
        firstNonBlank(child.version(), parent.version()),
        firstNonBlank(child.communityDefault(), parent.communityDefault()),
        child.timeoutMs() == null ? parent.timeoutMs() : child.timeoutMs(),
        child.retries() == null ? parent.retries() : child.retries(),
        child.port() == null ? parent.port() : child.port(),
        firstNonBlank(child.securityUsername(), parent.securityUsername()),
        firstNonBlank(child.authProtocol(), parent.authProtocol()),
        firstNonBlank(child.authPassword(), parent.authPassword()),
        firstNonBlank(child.privacyProtocol(), parent.privacyProtocol()),
        firstNonBlank(child.privacyPassword(), parent.privacyPassword())
    );
  }

  private MonitoringTemplateOids mergeOids(MonitoringTemplateOids parent, MonitoringTemplateOids child) {
    if (child == null) {
      return parent;
    }
    return new MonitoringTemplateOids(
        mergeStringMap(parent.discovery(), child.discovery()),
        mergeStringMap(parent.details(), child.details()),
        mergeStringMap(parent.interfaces(), child.interfaces())
    );
  }

  private MonitoringTemplateOids mergeOidsFirstWins(MonitoringTemplateOids primary, MonitoringTemplateOids secondary) {
    if (primary == null) {
      return secondary;
    }
    if (secondary == null) {
      return primary;
    }
    return new MonitoringTemplateOids(
        mergeStringMapFirstWins(primary.discovery(), secondary.discovery()),
        mergeStringMapFirstWins(primary.details(), secondary.details()),
        mergeStringMapFirstWins(primary.interfaces(), secondary.interfaces())
    );
  }

  private Map<String, String> mergeStringMap(Map<String, String> parent, Map<String, String> child) {
    Map<String, String> merged = new HashMap<>();
    if (parent != null) {
      merged.putAll(parent);
    }
    if (child != null) {
      merged.putAll(child.entrySet().stream()
          .filter(entry -> !isBlank(entry.getValue()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
    return Map.copyOf(merged);
  }

  private Map<String, String> mergeStringMapFirstWins(Map<String, String> primary, Map<String, String> secondary) {
    Map<String, String> merged = new LinkedHashMap<>();
    if (primary != null) {
      merged.putAll(primary);
    }
    if (secondary != null) {
      for (Map.Entry<String, String> entry : secondary.entrySet()) {
        if (!isBlank(entry.getValue())) {
          merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
      }
    }
    return Map.copyOf(merged);
  }

  private <V> Map<String, V> mergeTypedMap(Map<String, V> parent, Map<String, V> child) {
    Map<String, V> merged = new LinkedHashMap<>();
    if (parent != null) {
      merged.putAll(parent);
    }
    if (child != null) {
      merged.putAll(child);
    }
    return Map.copyOf(merged);
  }

  private Map<String, ZabbixDiscoveryRuleRuntime> mergeDiscoveryRules(
      Map<String, ZabbixDiscoveryRuleRuntime> parent,
      Map<String, ZabbixDiscoveryRuleRuntime> child
  ) {
    Map<String, ZabbixDiscoveryRuleRuntime> merged = new LinkedHashMap<>();
    if (parent != null) {
      merged.putAll(parent);
    }
    if (child == null) {
      return Map.copyOf(merged);
    }
    for (Map.Entry<String, ZabbixDiscoveryRuleRuntime> entry : child.entrySet()) {
      ZabbixDiscoveryRuleRuntime existing = merged.get(entry.getKey());
      if (existing == null) {
        merged.put(entry.getKey(), entry.getValue());
      } else {
        merged.put(entry.getKey(), mergeDiscoveryRule(existing, entry.getValue()));
      }
    }
    return Map.copyOf(merged);
  }

  private ZabbixDiscoveryRuleRuntime mergeDiscoveryRule(
      ZabbixDiscoveryRuleRuntime parent,
      ZabbixDiscoveryRuleRuntime child
  ) {
    Map<String, ZabbixItemRuntime> itemPrototypes = new LinkedHashMap<>();
    appendItemPrototypes(itemPrototypes, parent.itemPrototypes());
    appendItemPrototypes(itemPrototypes, child.itemPrototypes());

    Map<String, ZabbixTriggerRuntime> triggerPrototypes = new LinkedHashMap<>();
    appendTriggerPrototypes(triggerPrototypes, parent.triggerPrototypes());
    appendTriggerPrototypes(triggerPrototypes, child.triggerPrototypes());

    List<ZabbixGraphRecord> graphPrototypes = new ArrayList<>();
    if (parent.graphPrototypes() != null) {
      graphPrototypes.addAll(parent.graphPrototypes());
    }
    if (child.graphPrototypes() != null) {
      graphPrototypes.addAll(child.graphPrototypes());
    }

    return new ZabbixDiscoveryRuleRuntime(
        firstNonBlank(child.uuid(), parent.uuid()),
        firstNonBlank(child.key(), parent.key()),
        firstNonBlank(child.name(), parent.name()),
        firstNonBlank(child.type(), parent.type()),
        firstNonBlank(child.snmpOid(), parent.snmpOid()),
        firstNonBlank(child.masterItemKey(), parent.masterItemKey()),
        child.preprocessing() == null || child.preprocessing().isEmpty()
            ? parent.preprocessing()
            : child.preprocessing(),
        child.lldMacroPaths() == null || child.lldMacroPaths().isEmpty()
            ? parent.lldMacroPaths()
            : child.lldMacroPaths(),
        child.delaySeconds() == 0 ? parent.delaySeconds() : child.delaySeconds(),
        child.lifetimeSeconds() == 0 ? parent.lifetimeSeconds() : child.lifetimeSeconds(),
        child.filter() != null ? child.filter() : parent.filter(),
        List.copyOf(itemPrototypes.values()),
        List.copyOf(triggerPrototypes.values()),
        List.copyOf(graphPrototypes)
    );
  }

  private void appendItemPrototypes(Map<String, ZabbixItemRuntime> target, List<ZabbixItemRuntime> prototypes) {
    if (prototypes == null) {
      return;
    }
    for (ZabbixItemRuntime prototype : prototypes) {
      if (prototype == null || isBlank(prototype.key())) {
        continue;
      }
      target.put(prototype.key(), prototype);
    }
  }

  private void appendTriggerPrototypes(Map<String, ZabbixTriggerRuntime> target, List<ZabbixTriggerRuntime> prototypes) {
    if (prototypes == null) {
      return;
    }
    for (ZabbixTriggerRuntime prototype : prototypes) {
      if (prototype == null) {
        continue;
      }
      target.put(firstNonBlank(prototype.uuid(), prototype.expression()), prototype);
    }
  }

  private Map<String, String> mapItemSources(Map<String, ZabbixItemRuntime> items, String templateId) {
    Map<String, String> sources = new LinkedHashMap<>();
    if (items == null || items.isEmpty() || isBlank(templateId)) {
      return Map.of();
    }
    for (String key : items.keySet()) {
      sources.put(key, templateId);
    }
    return Map.copyOf(sources);
  }

  private ResolvedMonitoringTemplate emptyTemplate(String id) {
    return new ResolvedMonitoringTemplate(
        id,
        "SNMP",
        id,
        "",
        null,
        null,
        null,
        0,
        "1",
        "embedded",
        "1.0.0",
        defaultSnmp(),
        new MonitoringTemplateOids(
            Map.of(
                "sysName", "1.3.6.1.2.1.1.5.0",
                "sysDescr", "1.3.6.1.2.1.1.1.0",
                "sysObjectId", "1.3.6.1.2.1.1.2.0",
                "macAddress", "1.3.6.1.2.1.17.1.1.0",
                "serialNumber", "1.3.6.1.2.1.47.1.1.1.1.11.1",
                "model", "1.3.6.1.2.1.47.1.1.1.1.13.1"
            ),
            Map.of(
                "sysDescr", "1.3.6.1.2.1.1.1.0",
                "uptime", "1.3.6.1.2.1.1.3.0",
                "adminContact", "1.3.6.1.2.1.1.4.0",
                "location", "1.3.6.1.2.1.1.6.0",
                "hardwareVersion", "1.3.6.1.2.1.47.1.1.1.1.8.1",
                "bootVersion", "1.3.6.1.2.1.47.1.1.1.1.10.1"
            ),
            Map.of()
        ),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );
  }

  private MonitoringTemplateCoverageReportDto mergeCoverageReports(
      MonitoringTemplateCoverageReportDto parent,
      MonitoringTemplateCoverageReportDto child
  ) {
    Map<String, MonitoringTemplateFeatureSupportDto> merged = new LinkedHashMap<>();
    if (parent != null && parent.features() != null) {
      for (MonitoringTemplateFeatureSupportDto feature : parent.features()) {
        merged.put(feature.key(), feature);
      }
    }
    if (child != null && child.features() != null) {
      for (MonitoringTemplateFeatureSupportDto feature : child.features()) {
        MonitoringTemplateFeatureSupportDto existing = merged.get(feature.key());
        if (existing == null) {
          merged.put(feature.key(), feature);
          continue;
        }
        merged.put(feature.key(), new MonitoringTemplateFeatureSupportDto(
            feature.key(),
            feature.title(),
            existing.presentInTemplate() || feature.presentInTemplate(),
            existing.importSupported() || feature.importSupported(),
            existing.runtimeSupported() || feature.runtimeSupported(),
            existing.apiSupported() || feature.apiSupported(),
            existing.uiSupported() || feature.uiSupported(),
            firstNonBlank(feature.notes(), existing.notes())
        ));
      }
    }
    List<String> warnings = new ArrayList<>();
    if (parent != null && parent.warnings() != null) {
      warnings.addAll(parent.warnings());
    }
    if (child != null && child.warnings() != null) {
      warnings.addAll(child.warnings());
    }
    List<String> blockingErrors = new ArrayList<>();
    if (parent != null && parent.blockingErrors() != null) {
      blockingErrors.addAll(parent.blockingErrors());
    }
    if (child != null && child.blockingErrors() != null) {
      blockingErrors.addAll(child.blockingErrors());
    }
    return new MonitoringTemplateCoverageReportDto(
        List.copyOf(merged.values()),
        warnings.stream().distinct().toList(),
        blockingErrors.stream().distinct().toList()
    );
  }

  private MonitoringTemplateSnmp defaultSnmp() {
    return MonitoringTemplateSnmp.v2c("public", 3000, 1, 161);
  }

  private int specificityScore(ResolvedMonitoringTemplate definition) {
    if (defaultTemplateId.equals(definition.id())) {
      return 0;
    }
    if (isBlank(definition.vendor())) {
      return 0;
    }
    if (isBlank(definition.modelRegex())) {
      return 1;
    }
    return isBlank(resolveFirmwareCondition(definition)) ? 2 : 3;
  }

  private boolean matchesDevice(
      ResolvedMonitoringTemplate definition,
      String vendor,
      String model,
      String firmwareVersion
  ) {
    if (defaultTemplateId.equals(definition.id())) {
      return true;
    }
    if (isBlank(definition.vendor()) || isBlank(vendor)) {
      return false;
    }
    if (!definition.vendor().equalsIgnoreCase(vendor.trim())) {
      return false;
    }
    if (isBlank(definition.modelRegex())) {
      return matchesFirmware(definition, firmwareVersion);
    }
    if (isBlank(model)) {
      return false;
    }
    boolean modelMatch = Pattern.compile(definition.modelRegex(), Pattern.CASE_INSENSITIVE).matcher(model).find();
    if (!modelMatch) {
      return false;
    }
    return matchesFirmware(definition, firmwareVersion);
  }

  private boolean matchesFirmware(ResolvedMonitoringTemplate definition, String firmwareVersion) {
    String required = resolveFirmwareCondition(definition);
    if (isBlank(required)) {
      return true;
    }
    if (isBlank(firmwareVersion)) {
      return false;
    }
    return firmwareVersion.trim().toLowerCase().contains(required.trim().toLowerCase());
  }

  private String resolveFirmwareCondition(ResolvedMonitoringTemplate definition) {
    // Firmware condition currently exists only for uploaded templates (metadata column).
    return resolveUploadedFirmware(definition.id());
  }

  private int parseDelaySeconds(String value, int fallback) {
    if (isBlank(value)) {
      return fallback;
    }
    String trimmed = value.trim().toLowerCase();
    try {
      if (trimmed.endsWith("h")) {
        return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 3600;
      }
      if (trimmed.endsWith("m")) {
        return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 60;
      }
      if (trimmed.endsWith("d")) {
        return Integer.parseInt(trimmed.substring(0, trimmed.length() - 1)) * 86400;
      }
      return Integer.parseInt(trimmed);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private int parseLifetimeSeconds(String value, int fallback) {
    return parseDelaySeconds(value, fallback);
  }

  private String normalizeSnmpOid(String oid) {
    if (isBlank(oid)) {
      return oid;
    }
    return switch (oid) {
      case "SNMPv2-MIB::sysContact.0" -> "1.3.6.1.2.1.1.4.0";
      case "IF-MIB::ifName" -> "1.3.6.1.2.1.31.1.1.1.1";
      default -> oid
          .replace("IF-MIB::ifHCInOctets", "1.3.6.1.2.1.31.1.1.1.6")
          .replace("IF-MIB::ifHCOutOctets", "1.3.6.1.2.1.31.1.1.1.10")
          .replace("IF-MIB::ifOutErrors", "1.3.6.1.2.1.2.2.1.20")
          .replace("IF-MIB::ifName", "1.3.6.1.2.1.31.1.1.1.1");
    };
  }

  private JsonNode selectTemplateNode(JsonNode exportNode, MonitoringTemplateManifestEntry entry) {
    if (exportNode == null || exportNode.path("templates").isMissingNode()) {
      return null;
    }
    JsonNode templatesNode = exportNode.path("templates");
    if (!templatesNode.isArray()) {
      return null;
    }
    if (isBlank(entry.zabbixTemplate())) {
      return templatesNode.path(0);
    }
    for (JsonNode templateNode : templatesNode) {
      String templateName = templateNode.path("template").asText("");
      String visibleName = templateNode.path("name").asText("");
      if (entry.zabbixTemplate().equals(templateName) || entry.zabbixTemplate().equals(visibleName)) {
        return templateNode;
      }
    }
    return templatesNode.path(0);
  }

  private MonitoringTemplateCoverageReportDto buildCoverageReport(
      JsonNode exportNode,
      JsonNode templateNode,
      Map<String, ZabbixItemRuntime> items,
      Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules
  ) {
    List<MonitoringTemplateFeatureSupportDto> features = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    boolean hasItemPrototypes = discoveryRules.values().stream().anyMatch(rule -> !rule.itemPrototypes().isEmpty());
    boolean hasTriggerPrototypes = discoveryRules.values().stream().anyMatch(rule -> !rule.triggerPrototypes().isEmpty());
    boolean hasGraphPrototypes = discoveryRules.values().stream().anyMatch(rule -> !rule.graphPrototypes().isEmpty());
    boolean hasDependentItems = items.values().stream().anyMatch(ZabbixItemRuntime::isDependent);
    boolean hasCalculatedItems = items.values().stream().anyMatch(ZabbixItemRuntime::isCalculated);
    boolean hasHttpItems = items.values().stream().anyMatch(item -> "HTTP_AGENT".equalsIgnoreCase(item.type()));
    boolean hasJmxItems = items.values().stream().anyMatch(item -> "JMX".equalsIgnoreCase(item.type()));
    boolean hasIpmiItems = items.values().stream().anyMatch(item -> "IPMI".equalsIgnoreCase(item.type()));
    boolean hasAgentItems = items.values().stream().anyMatch(item ->
        "ZABBIX_PASSIVE".equalsIgnoreCase(item.type()) || "ZABBIX_ACTIVE".equalsIgnoreCase(item.type()));
    Set<String> preprocessingTypesInTemplate = collectPreprocessingTypes(items, discoveryRules);
    Set<String> supportedPreprocessingTypes = supportedPreprocessingTypes();
    Set<String> unsupportedPreprocessing = preprocessingTypesInTemplate.stream()
        .filter(type -> !supportedPreprocessingTypes.contains(type))
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    boolean hasRecoveryExpressions = anyNestedChild(templateNode, "items", "triggers", "recovery_expression")
        || anyNestedChild(templateNode, "discovery_rules", "trigger_prototypes", "recovery_expression");
    boolean hasTriggerDependencies = anyNestedChild(templateNode, "items", "triggers", "dependencies")
        || anyNestedChild(templateNode, "discovery_rules", "trigger_prototypes", "dependencies");
    boolean hasTriggerTags = anyNestedChild(templateNode, "items", "triggers", "tags")
        || anyNestedChild(templateNode, "discovery_rules", "trigger_prototypes", "tags");
    boolean hasManualClose = anyNestedChild(templateNode, "items", "triggers", "manual_close")
        || anyNestedChild(templateNode, "discovery_rules", "trigger_prototypes", "manual_close");

    features.add(feature("template_groups", "Template groups", hasChild(exportNode, "template_groups"), false, false, false, false,
        "Группы шаблонов из экспорта пока не сохраняются как отдельная доменная сущность."));
    features.add(feature("host_groups", "Host groups", hasChild(exportNode, "host_groups"), false, false, false, false,
        "Host groups из экспорта пока не материализуются в runtime."));
    features.add(feature("items", "Items", hasChild(templateNode, "items"), true, true, true, true,
        "Базовые items импортируются и исполняются через runtime."));
    features.add(feature("discovery_rules", "Discovery rules", hasChild(templateNode, "discovery_rules"), true, true, true, true,
        "LLD rules исполняются и доступны через runtime state API."));
    features.add(feature("item_prototypes", "Item prototypes", hasItemPrototypes, true, true, true, true,
        "Прототипы материализуются в item state и history."));
    features.add(feature("trigger_prototypes", "Trigger prototypes", hasTriggerPrototypes, true, true, true, true,
        "Trigger prototypes участвуют в генерации событий."));
    features.add(feature("graph_prototypes", "Graph prototypes", hasGraphPrototypes, true, false, true, false,
        "Графы пока используются как metadata и не рендерятся как отдельный UI-виджет."));
    features.add(feature("valuemaps", "Value maps", hasChild(templateNode, "valuemaps"), true, true, true, true,
        "Value maps применяются при выдаче current item state."));
    features.add(feature("graphs", "Graphs", hasChild(templateNode, "graphs"), true, false, true, false,
        "Графы шаблонов импортируются как metadata."));
    features.add(feature("macros", "Macros", hasChild(templateNode, "macros"), true, true, false, false,
        "Поддерживаются только template macros, используемые в runtime шаблона."));
    features.add(feature("dashboards", "Template dashboards", hasChild(templateNode, "dashboards"), false, false, false, false,
        "Template dashboards пока не импортируются."));
    features.add(feature("httptests", "Web scenarios", hasChild(templateNode, "httptests"), false, false, false, false,
        "Web scenarios пока не импортируются и не исполняются."));
    features.add(feature("host_prototypes", "Host prototypes", anyNestedChild(templateNode, "discovery_rules", "host_prototypes"), false, false, false, false,
        "Host prototypes пока не поддерживаются."));
    features.add(feature("lld_overrides", "LLD overrides", anyNestedChild(templateNode, "discovery_rules", "overrides"), false, false, false, false,
        "LLD overrides пока не поддерживаются."));
    features.add(feature("lld_macro_paths", "LLD macro paths", anyNestedChild(templateNode, "discovery_rules", "lld_macro_paths"), true, true, true, true,
        "Пути для LLD macros поддерживаются для dependent discovery rules с JSON payload от master item."));
    features.add(feature("preprocessing_runtime", "Preprocessing runtime", !preprocessingTypesInTemplate.isEmpty(), true, true, true, true,
        unsupportedPreprocessing.isEmpty()
            ? "Часто используемые preprocessing steps, включая JAVASCRIPT (timeout, discard/error semantics), исполняются в runtime."
            : "Часть шагов пока без runtime-исполнения: " + String.join(", ", unsupportedPreprocessing)));
    features.add(feature("trigger_recovery_expression", "Trigger recovery expression", hasRecoveryExpressions, true, true, true, true,
        "Recovery expression импортируется и участвует в закрытии события."));
    features.add(feature("trigger_dependencies", "Trigger dependencies", hasTriggerDependencies, true, true, true, true,
        "Зависимости триггеров импортируются и блокируют дочерние события при активном родителе."));
    features.add(feature("trigger_tags", "Trigger tags", hasTriggerTags, true, true, true, false,
        "Базовые trigger tags импортируются и доступны в runtime metadata."));
    features.add(feature("trigger_manual_close", "Trigger manual close", hasManualClose, true, true, true, false,
        "manual_close импортируется как runtime metadata."));
    features.add(feature("dependent_items", "Dependent items", hasDependentItems, true, true, true, true,
        "Dependent items поддерживаются через derived executor."));
    features.add(feature("calculated_items", "Calculated items", hasCalculatedItems, true, true, true, true,
        "Calculated items исполняются ограниченным арифметическим runtime-движком."));
    features.add(feature("http_items", "HTTP agent items", hasHttpItems, true, false, false, false,
        "HTTP agent items пока только импортируются."));
    features.add(feature("jmx_items", "JMX items", hasJmxItems, true, false, false, false,
        "JMX items пока только импортируются."));
    features.add(feature("ipmi_items", "IPMI items", hasIpmiItems, true, false, false, false,
        "IPMI items пока только импортируются."));
    features.add(feature("zabbix_agent_items", "Zabbix agent items", hasAgentItems, true, false, false, false,
        "Zabbix agent items пока только импортируются."));

    for (MonitoringTemplateFeatureSupportDto feature : features) {
      if (feature.presentInTemplate() && !feature.runtimeSupported()) {
        warnings.add(feature.title() + ": " + feature.notes());
      }
    }
    if (!unsupportedPreprocessing.isEmpty()) {
      warnings.add("Неподдержанные preprocessing steps: " + String.join(", ", unsupportedPreprocessing));
    }
    return new MonitoringTemplateCoverageReportDto(List.copyOf(features), List.copyOf(warnings), List.of());
  }

  private MonitoringTemplateFeatureSupportDto feature(
      String key,
      String title,
      boolean presentInTemplate,
      boolean importSupported,
      boolean runtimeSupported,
      boolean apiSupported,
      boolean uiSupported,
      String notes
  ) {
    return new MonitoringTemplateFeatureSupportDto(
        key,
        title,
        presentInTemplate,
        importSupported,
        runtimeSupported,
        apiSupported,
        uiSupported,
        notes
    );
  }

  private String dependencyKeyFromRecord(com.networkscanner.backend.monitoring.dto.ZabbixTriggerDependencyRecord dependency) {
    if (dependency == null) {
      return null;
    }
    if (!isBlank(dependency.expression())) {
      return dependency.expression().trim();
    }
    return dependency.name() == null ? null : dependency.name().trim();
  }

  private boolean isEnabledFlag(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = value.trim().toLowerCase();
    return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
  }

  private Set<String> collectPreprocessingTypes(
      Map<String, ZabbixItemRuntime> items,
      Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules
  ) {
    Set<String> types = new java.util.LinkedHashSet<>();
    for (ZabbixItemRuntime item : items.values()) {
      collectPreprocessingTypes(types, item.preprocessing());
    }
    for (ZabbixDiscoveryRuleRuntime rule : discoveryRules.values()) {
      for (ZabbixItemRuntime item : rule.itemPrototypes()) {
        collectPreprocessingTypes(types, item.preprocessing());
      }
    }
    return types;
  }

  private void collectPreprocessingTypes(Set<String> target, List<ZabbixPreprocessingStep> preprocessing) {
    if (preprocessing == null) {
      return;
    }
    for (ZabbixPreprocessingStep step : preprocessing) {
      if (step != null && !isBlank(step.type())) {
        target.add(step.type().trim().toUpperCase());
      }
    }
  }

  private Set<String> supportedPreprocessingTypes() {
    return Set.of(
        "MULTIPLIER",
        "SIMPLE_CHANGE",
        "CHANGE_PER_SECOND",
        "TRIM",
        "LTRIM",
        "RTRIM",
        "STR_REPLACE",
        "REGEX",
        "MATCHES_REGEX",
        "NOT_MATCHES_REGEX",
        "IN_RANGE",
        "JSONPATH",
        "XMLPATH",
        "CHECK_JSON_ERROR",
        "CHECK_XML_ERROR",
        "CHECK_REGEX_ERROR",
        "CHECK_NOT_SUPPORTED",
        "JAVASCRIPT",
        "XML_TO_JSON",
        "CSV_TO_JSON",
        "SNMP_WALK_TO_JSON",
        "BOOL_TO_DECIMAL",
        "HEX_TO_DECIMAL",
        "OCTAL_TO_DECIMAL",
        "DISCARD_UNCHANGED",
        "DISCARD_UNCHANGED_HEARTBEAT",
        "SNMP_WALK_VALUE"
    );
  }

  private boolean hasChild(JsonNode node, String child) {
    return node != null && node.has(child) && !node.path(child).isEmpty();
  }

  private boolean anyNestedChild(JsonNode node, String arrayField, String child) {
    if (node == null || !node.has(arrayField) || !node.path(arrayField).isArray()) {
      return false;
    }
    for (JsonNode item : node.path(arrayField)) {
      if (item.has(child) && !item.path(child).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean anyNestedChild(JsonNode node, String firstArray, String secondArray, String child) {
    if (node == null || !node.has(firstArray) || !node.path(firstArray).isArray()) {
      return false;
    }
    for (JsonNode first : node.path(firstArray)) {
      if (first.has(secondArray) && first.path(secondArray).isArray()) {
        for (JsonNode second : first.path(secondArray)) {
          if (second.has(child) && !second.path(child).isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private MonitoringTemplateDetailsDto toTemplateDetails(ResolvedMonitoringTemplate template) {
    MonitoringTemplateSummaryDto summary = new MonitoringTemplateSummaryDto(
        template.id(),
        template.type(),
        template.name(),
        template.description(),
        resolveUploadedBy(template.id()),
        resolveUploadedByDisplayName(template.id()),
        template.extendsTemplate(),
        template.vendor(),
        resolveUploadedModel(template.id()),
        template.modelRegex(),
        resolveUploadedFirmware(template.id()),
        template.priority(),
        template.schemaVersion(),
        template.packVersion(),
        template.templateVersion(),
        uploadedTemplateIds.contains(template.id()) ? MonitoringTemplateSource.UPLOADED : MonitoringTemplateSource.SYSTEM,
        uploadedTemplateIds.contains(template.id())
    );
    List<MonitoringTemplateItemDto> itemDtos = buildTemplateItemDtos(template);
    List<MonitoringTemplateDiscoveryRuleDto> discoveryRuleDtos = template.discoveryRules().values().stream()
        .map(rule -> new MonitoringTemplateDiscoveryRuleDto(
            rule.key(),
            rule.name(),
            firstNonBlank(rule.type(), "SNMP_AGENT"),
            rule.delaySeconds(),
            rule.lifetimeSeconds(),
            rule.filter() != null,
            rule.itemPrototypes().size(),
            rule.triggerPrototypes().size(),
            rule.graphPrototypes().size()
        ))
        .sorted(Comparator.comparing(MonitoringTemplateDiscoveryRuleDto::key))
        .toList();
    List<MonitoringTemplateTriggerDto> triggerDtos = template.triggers().values().stream()
        .map(trigger -> new MonitoringTemplateTriggerDto(
            trigger.uuid(),
            trigger.name(),
            trigger.expression(),
            trigger.priority(),
            trigger.discoveryPrototype(),
            trigger.discoveryRuleKey()
        ))
        .sorted(Comparator.comparing(MonitoringTemplateTriggerDto::name))
        .toList();
    List<MonitoringTemplateValueMapDto> valueMaps = template.valueMaps().values().stream()
        .map(valueMap -> new MonitoringTemplateValueMapDto(valueMap.name(), valueMap.mappings()))
        .sorted(Comparator.comparing(MonitoringTemplateValueMapDto::name))
        .toList();
    List<String> graphNames = template.graphs().stream()
        .map(graph -> firstNonBlank(graph.name(), "graph"))
        .sorted()
        .toList();
    return new MonitoringTemplateDetailsDto(
        summary,
        template.coverage(),
        itemDtos,
        discoveryRuleDtos,
        triggerDtos,
        valueMaps,
        graphNames
    );
  }

  private List<MonitoringTemplateItemDto> buildTemplateItemDtos(ResolvedMonitoringTemplate template) {
    Map<String, MonitoringTemplateItemDto> byKey = new LinkedHashMap<>();
    for (ZabbixItemRuntime item : template.items().values()) {
      byKey.put(item.key(), toItemDto(item));
    }
    for (ZabbixDiscoveryRuleRuntime rule : template.discoveryRules().values()) {
      if (rule.itemPrototypes() == null) {
        continue;
      }
      for (ZabbixItemRuntime prototype : rule.itemPrototypes()) {
        if (prototype == null || isBlank(prototype.key())) {
          continue;
        }
        byKey.putIfAbsent(prototype.key(), toItemDto(prototype));
      }
    }
    return byKey.values().stream()
        .sorted(Comparator.comparing(MonitoringTemplateItemDto::key))
        .toList();
  }

  private MonitoringTemplateItemDto toItemDto(ZabbixItemRuntime item) {
    String preprocessing = item.preprocessing() == null || item.preprocessing().isEmpty()
        ? ""
        : item.preprocessing().stream().map(ZabbixPreprocessingStep::type).collect(Collectors.joining(" -> "));
    return new MonitoringTemplateItemDto(
        item.key(),
        item.name(),
        item.type(),
        item.valueType(),
        item.units(),
        item.delaySeconds(),
        item.snmpOid(),
        item.masterItemKey(),
        item.params(),
        preprocessing,
        item.valueMapName(),
        item.discoveryPrototype(),
        item.discoveryRuleKey(),
        isRuntimeSupported(item)
    );
  }

  private boolean isRuntimeSupported(ZabbixItemRuntime item) {
    return item.isSnmpBased() || item.isDependent() || item.isCalculated();
  }

  private MonitoringTemplateDiffSummaryDto buildDiffSummary(
      ResolvedMonitoringTemplate existing,
      ResolvedMonitoringTemplate candidate
  ) {
    if (existing == null) {
      return new MonitoringTemplateDiffSummaryDto(false,
          candidate.items().size(),
          candidate.discoveryRules().size(),
          candidate.triggers().size(),
          candidate.valueMaps().size(),
          candidate.graphs().size());
    }
    return new MonitoringTemplateDiffSummaryDto(
        true,
        candidate.items().size() - existing.items().size(),
        candidate.discoveryRules().size() - existing.discoveryRules().size(),
        candidate.triggers().size() - existing.triggers().size(),
        candidate.valueMaps().size() - existing.valueMaps().size(),
        candidate.graphs().size() - existing.graphs().size()
    );
  }

  private String firstNonBlank(String value, String fallback) {
    return isBlank(value) ? fallback : value;
  }

  private String resolveUploadedBy(String templateId) {
    if (!uploadedTemplateIds.contains(templateId)) {
      return "Система";
    }
    return firstNonBlank(uploadedTemplateUsers.get(templateId), "-");
  }

  private String resolveUploadedByDisplayName(String templateId) {
    if (!uploadedTemplateIds.contains(templateId)) {
      return "Система";
    }
    return firstNonBlank(uploadedTemplateDisplayUsers.get(templateId), resolveUploadedBy(templateId));
  }

  private String resolveUploadedModel(String templateId) {
    if (!uploadedTemplateIds.contains(templateId)) {
      return null;
    }
    UploadedTemplateMetadata meta = uploadedTemplateMetadata.get(templateId);
    if (meta == null) {
      return null;
    }
    return meta.model();
  }

  private String resolveUploadedFirmware(String templateId) {
    if (!uploadedTemplateIds.contains(templateId)) {
      return null;
    }
    UploadedTemplateMetadata meta = uploadedTemplateMetadata.get(templateId);
    if (meta == null) {
      return null;
    }
    return meta.firmware();
  }

  private String resolveDisplayName(String uploadedBy) {
    if (isBlank(uploadedBy)) {
      return null;
    }
    return appUserRepository.findByEmailIgnoreCase(uploadedBy)
        .map(user -> firstNonBlank(user.getDisplayName(), uploadedBy))
        .orElse(uploadedBy);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private JsonNode readTemplateRoot(String templateYaml) throws IOException {
    String normalized = normalizeZabbixYaml(templateYaml);
    try {
      return yamlMapper.readTree(normalized);
    } catch (IOException yamlException) {
      try {
        return jsonMapper.readTree(templateYaml);
      } catch (IOException jsonException) {
        yamlException.addSuppressed(jsonException);
        throw yamlException;
      }
    }
  }

  private String normalizeZabbixYaml(String source) {
    String[] lines = source.replace("\r\n", "\n").split("\n", -1);
    StringBuilder normalized = new StringBuilder(source.length() + 256);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.trim().equals("-") && i + 1 < lines.length) {
        String next = lines[i + 1];
        int indent = leadingSpaces(line);
        int nextIndent = leadingSpaces(next);
        if (nextIndent >= indent) {
          normalized.append(" ".repeat(indent)).append("- ").append(next.trim()).append('\n');
          i++;
          continue;
        }
      }
      normalized.append(line).append('\n');
    }
    return normalized.toString();
  }

  private int leadingSpaces(String value) {
    int index = 0;
    while (index < value.length() && value.charAt(index) == ' ') {
      index++;
    }
    return index;
  }

  private record DerivedTemplateViews(
      MonitoringTemplateOids oids,
      Map<String, UnitDefinition> units,
      Map<String, PreprocessingFunctionDefinition> preprocessingFunctions,
      Map<String, MetricDefinition> metrics
  ) {
  }

  private record LoadedTemplates(
      String defaultTemplateId,
      Map<String, ResolvedMonitoringTemplate> templates,
      Set<String> uploadedTemplateIds,
      Map<String, String> uploadedTemplateUsers,
      Map<String, String> uploadedTemplateDisplayUsers,
      Map<String, UploadedTemplateMetadata> uploadedTemplateMetadata
  ) {
  }

  private record UploadedTemplateMetadata(String model, String firmware) {
  }

  @FunctionalInterface
  private interface TemplateYamlReader {
    String readYaml(String fileName) throws IOException;
  }
}
