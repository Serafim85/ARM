package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.MonitoredDeviceItemService;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemSelectionDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceItemEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceItemEntityId;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceItemRepository;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoredDeviceItemServiceImpl implements MonitoredDeviceItemService {

  private static final String INSTANCE_KEY_STATIC = "";
  private static final String INSTANCE_KEY_ALL_DISCOVERY = "*";

  private final MonitoredDeviceItemRepository itemRepository;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final ZabbixRuntimeStateService runtimeStateService;
  private final MonitoringEventRepository monitoringEventRepository;

  public MonitoredDeviceItemServiceImpl(
      MonitoredDeviceItemRepository itemRepository,
      MonitoredDeviceRepository monitoredDeviceRepository,
      ZabbixRuntimeStateService runtimeStateService,
      MonitoringEventRepository monitoringEventRepository
  ) {
    this.itemRepository = itemRepository;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.runtimeStateService = runtimeStateService;
    this.monitoringEventRepository = monitoringEventRepository;
  }

  @Override
  @Transactional
  public void seedDefaultsForDevice(MonitoredDeviceEntity device, ResolvedMonitoringTemplate template) {
    List<CatalogEntry> catalog = buildCatalog(template);
    replaceRowsForDevice(device, catalog, true);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MonitoringDeviceItemDto> listDeviceItems(MonitoredDeviceEntity device, ResolvedMonitoringTemplate template) {
    List<CatalogEntry> catalog = buildCatalog(template);
    Set<ItemActivationKey> activeKeys = loadActivationKeys(device.getId());
    boolean fallbackAllActive = !device.isItemAllowlistInitialized();

    return catalog.stream()
        .map(entry -> new MonitoringDeviceItemDto(
            entry.itemUuid(),
            entry.itemKey(),
            entry.name(),
            entry.itemType(),
            entry.discoveryPrototype(),
            entry.discoveryRuleKey(),
            toApiInstanceKey(entry.instanceKey()),
            fallbackAllActive || activeKeys.contains(new ItemActivationKey(entry.itemUuid(), entry.instanceKey()))
        ))
        .sorted(Comparator.comparing(MonitoringDeviceItemDto::discoveryPrototype)
            .thenComparing(MonitoringDeviceItemDto::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(MonitoringDeviceItemDto::itemKey, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  @Override
  @Transactional
  public List<MonitoringDeviceItemDto> replaceActiveItems(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      List<MonitoringDeviceItemSelectionDto> activeItems
  ) {
    List<MonitoringDeviceItemSelectionDto> requested = activeItems == null ? List.of() : activeItems;
    List<CatalogEntry> catalog = buildCatalog(template);
    Map<ItemActivationKey, CatalogEntry> catalogByKey = catalog.stream()
        .collect(Collectors.toMap(
            entry -> new ItemActivationKey(entry.itemUuid(), entry.instanceKey()),
            Function.identity(),
            (left, right) -> left,
            LinkedHashMap::new
        ));

    List<CatalogEntry> selectedEntries = new ArrayList<>();
    for (MonitoringDeviceItemSelectionDto selected : requested) {
      ItemActivationKey selectionKey = normalizeSelection(selected, catalogByKey);
      CatalogEntry catalogEntry = catalogByKey.get(selectionKey);
      if (catalogEntry == null) {
        throw new IllegalArgumentException("Неизвестный item для устройства: " + selected.itemUuid());
      }
      selectedEntries.add(catalogEntry);
    }

    List<CatalogEntry> deduplicated = selectedEntries.stream().distinct().toList();
    replaceRowsForDevice(device, deduplicated, false);
    return listDeviceItems(device, template);
  }

  @Override
  @Transactional
  public void deactivateItem(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      String itemUuid,
      String instanceKey
  ) {
    List<CatalogEntry> catalog = buildCatalog(template);
    Map<String, List<CatalogEntry>> catalogByUuid = catalog.stream()
        .collect(Collectors.groupingBy(CatalogEntry::itemUuid, LinkedHashMap::new, Collectors.toList()));
    List<CatalogEntry> byUuid = catalogByUuid.get(itemUuid);
    if (byUuid == null || byUuid.isEmpty()) {
      throw new IllegalArgumentException("Item не найден в текущем каталоге устройства.");
    }
    ItemActivationKey key;
    if (instanceKey != null && !instanceKey.isBlank()) {
      key = new ItemActivationKey(itemUuid, normalizeInstanceKey(instanceKey));
    } else if (byUuid.size() == 1) {
      key = new ItemActivationKey(itemUuid, byUuid.get(0).instanceKey());
    } else {
      throw new IllegalArgumentException("Для item необходимо указать instanceKey.");
    }

    MonitoredDeviceItemEntityId id = new MonitoredDeviceItemEntityId(device.getId(), key.itemUuid(), key.instanceKey());
    itemRepository.findById(id).ifPresent(this::purgeRuntimeState);
    itemRepository.deleteById(id);
    device.setItemAllowlistInitialized(true);
    monitoredDeviceRepository.save(device);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<ItemActivationKey> loadActivationKeys(Long deviceId) {
    return itemRepository.findByDeviceId(deviceId).stream()
        .map(item -> new ItemActivationKey(item.getItemUuid(), normalizeInstanceKey(item.getInstanceKey())))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private void replaceRowsForDevice(
      MonitoredDeviceEntity device,
      List<CatalogEntry> nextEntries,
      boolean addMissingByDefault
  ) {
    Map<ItemActivationKey, MonitoredDeviceItemEntity> existingByKey = itemRepository.findByDeviceId(device.getId()).stream()
        .collect(Collectors.toMap(
            row -> new ItemActivationKey(row.getItemUuid(), normalizeInstanceKey(row.getInstanceKey())),
            Function.identity(),
            (left, right) -> left,
            LinkedHashMap::new
        ));

    Set<ItemActivationKey> nextKeys = nextEntries.stream()
        .map(entry -> new ItemActivationKey(entry.itemUuid(), entry.instanceKey()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<MonitoredDeviceItemEntity> toSave = new ArrayList<>();
    OffsetDateTime now = OffsetDateTime.now();

    for (CatalogEntry entry : nextEntries) {
      ItemActivationKey key = new ItemActivationKey(entry.itemUuid(), entry.instanceKey());
      MonitoredDeviceItemEntity existing = existingByKey.remove(key);
      if (existing == null) {
        MonitoredDeviceItemEntity created = new MonitoredDeviceItemEntity();
        created.setDeviceId(device.getId());
        created.setItemUuid(entry.itemUuid());
        created.setInstanceKey(entry.instanceKey());
        created.setCreatedAt(now);
        applyEntry(created, entry, now);
        toSave.add(created);
      } else {
        applyEntry(existing, entry, now);
        toSave.add(existing);
      }
    }

    if (!addMissingByDefault) {
      for (MonitoredDeviceItemEntity removed : existingByKey.values()) {
        purgeRuntimeState(removed);
      }
      itemRepository.deleteAll(existingByKey.values());
    } else {
      List<MonitoredDeviceItemEntity> stale = existingByKey.values().stream()
          .filter(existing -> nextKeys.stream().noneMatch(key -> matches(existing, key)))
          .toList();
      if (!stale.isEmpty()) {
        stale.forEach(this::purgeRuntimeState);
        itemRepository.deleteAll(stale);
      }
    }

    itemRepository.saveAll(toSave);
    device.setItemAllowlistInitialized(true);
    monitoredDeviceRepository.save(device);
  }

  private boolean matches(MonitoredDeviceItemEntity entity, ItemActivationKey key) {
    return Objects.equals(entity.getItemUuid(), key.itemUuid())
        && Objects.equals(normalizeInstanceKey(entity.getInstanceKey()), key.instanceKey());
  }

  private void applyEntry(MonitoredDeviceItemEntity target, CatalogEntry source, OffsetDateTime updatedAt) {
    target.setItemKey(source.itemKey());
    target.setName(source.name());
    target.setItemType(source.itemType());
    target.setDiscoveryPrototype(source.discoveryPrototype());
    target.setDiscoveryRuleKey(source.discoveryRuleKey());
    target.setSourceTemplateId(source.sourceTemplateId());
    target.setUpdatedAt(updatedAt);
  }

  private void purgeRuntimeState(MonitoredDeviceItemEntity removed) {
    monitoringEventRepository.resolveOpenEventsByItem(
        removed.getDeviceId(),
        removed.getItemKey(),
        normalizeInstanceKey(removed.getInstanceKey()),
        OffsetDateTime.now()
    );
    runtimeStateService.removeItemState(
        removed.getDeviceId(),
        removed.getItemUuid(),
        normalizeInstanceKey(removed.getInstanceKey())
    );
  }

  private ItemActivationKey normalizeSelection(
      MonitoringDeviceItemSelectionDto selected,
      Map<ItemActivationKey, CatalogEntry> catalogByKey
  ) {
    if (selected == null || selected.itemUuid() == null || selected.itemUuid().isBlank()) {
      throw new IllegalArgumentException("Каждый item должен содержать itemUuid.");
    }
    String normalizedUuid = selected.itemUuid().trim();
    String normalizedInstance = normalizeApiInstanceKey(selected.instanceKey());
    ItemActivationKey exact = new ItemActivationKey(normalizedUuid, normalizedInstance);
    if (catalogByKey.containsKey(exact)) {
      return exact;
    }
    if (selected.instanceKey() == null || selected.instanceKey().isBlank()) {
      List<ItemActivationKey> byUuid = catalogByKey.keySet().stream()
          .filter(key -> key.itemUuid().equals(normalizedUuid))
          .toList();
      if (byUuid.size() == 1) {
        return byUuid.get(0);
      }
    }
    return exact;
  }

  private List<CatalogEntry> buildCatalog(ResolvedMonitoringTemplate template) {
    List<CatalogEntry> entries = new ArrayList<>();
    if (template.items() != null) {
      for (ZabbixItemRuntime item : template.items().values()) {
        if (isBlank(item.uuid()) || isBlank(item.key())) {
          continue;
        }
        entries.add(new CatalogEntry(
            item.uuid(),
            INSTANCE_KEY_STATIC,
            item.key(),
            firstNonBlank(item.name(), item.key()),
            firstNonBlank(item.type(), "SNMP_AGENT"),
            false,
            null,
            sourceTemplateId(template, item.key())
        ));
      }
    }
    if (template.discoveryRules() != null) {
      for (ZabbixDiscoveryRuleRuntime rule : template.discoveryRules().values()) {
        for (ZabbixItemRuntime prototype : rule.itemPrototypes()) {
          if (isBlank(prototype.uuid()) || isBlank(prototype.key())) {
            continue;
          }
          entries.add(new CatalogEntry(
              prototype.uuid(),
              INSTANCE_KEY_ALL_DISCOVERY,
              prototype.key(),
              firstNonBlank(prototype.name(), prototype.key()),
              firstNonBlank(prototype.type(), "SNMP_AGENT"),
              true,
              rule.key(),
              sourceTemplateId(template, prototype.key())
          ));
        }
      }
    }
    return entries.stream().distinct().toList();
  }

  private String sourceTemplateId(ResolvedMonitoringTemplate template, String itemKey) {
    if (template.itemTemplateIds() == null) {
      return template.id();
    }
    return template.itemTemplateIds().getOrDefault(itemKey, template.id());
  }

  private String normalizeApiInstanceKey(String value) {
    if (value == null || value.isBlank()) {
      return INSTANCE_KEY_STATIC;
    }
    String normalized = value.trim();
    return normalized;
  }

  private String normalizeInstanceKey(String value) {
    return value == null ? INSTANCE_KEY_STATIC : value;
  }

  private String toApiInstanceKey(String value) {
    String normalized = normalizeInstanceKey(value);
    if (INSTANCE_KEY_STATIC.equals(normalized) || INSTANCE_KEY_ALL_DISCOVERY.equals(normalized)) {
      return null;
    }
    return normalized;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record CatalogEntry(
      String itemUuid,
      String instanceKey,
      String itemKey,
      String name,
      String itemType,
      boolean discoveryPrototype,
      String discoveryRuleKey,
      String sourceTemplateId
  ) {
  }
}
