package com.networkscanner.backend.network.scan.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networkscanner.backend.accessprofiles.api.AccessProfileResolver;
import com.networkscanner.backend.monitoring.api.DependentItemSnapshotPreprocessor;
import com.networkscanner.backend.monitoring.dto.*;
import com.networkscanner.backend.monitoring.impl.JsPreprocessingCompatService;
import com.networkscanner.backend.monitoring.impl.MonitoringPreprocessingEngine;
import com.networkscanner.backend.network.scan.api.ReverseDnsLookupService;
import com.networkscanner.backend.network.scan.api.ScanRunContext;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.DiscoveryProbeConfig;
import com.networkscanner.backend.network.scan.dto.ScanExecutionResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.util.IpRangeParser;
import com.networkscanner.backend.network.scan.util.SnmpDeviceTypeClassifier;
import com.networkscanner.backend.network.scan.util.SnmpWalkJsonSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class SnmpScanServiceImpl implements SnmpScanService {

  private static final Logger log = LoggerFactory.getLogger(SnmpScanServiceImpl.class);
  private static final int MAX_SNMP_GET_BATCH_SIZE = 25;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final OID SYS_NAME = new OID("1.3.6.1.2.1.1.5.0");
  private static final OID SYS_DESCR = new OID("1.3.6.1.2.1.1.1.0");
  private static final OID SYS_OBJECT_ID = new OID("1.3.6.1.2.1.1.2.0");
  private static final OID BRIDGE_MAC = new OID("1.3.6.1.2.1.17.1.1.0");
  private static final OID ENT_PHYSICAL_SERIAL = new OID("1.3.6.1.2.1.47.1.1.1.1.11.1");
  private static final OID ENT_PHYSICAL_MODEL = new OID("1.3.6.1.2.1.47.1.1.1.1.13.1");
  /** ENTITY-MIB entPhysicalSerialNum column (any row; .1 often missing on hosts). */
  private static final OID ENT_PHYSICAL_SERIAL_COLUMN = new OID("1.3.6.1.2.1.47.1.1.1.1.11");
  /** ENTITY-MIB entPhysicalModelName column. */
  private static final OID ENT_PHYSICAL_MODEL_COLUMN = new OID("1.3.6.1.2.1.47.1.1.1.1.13");
  /** IF-MIB ifPhysAddress — MAC per interface (works on Linux net-snmp; bridge MIB often does not). */
  private static final OID IF_PHYS_ADDRESS_BASE = new OID("1.3.6.1.2.1.2.2.1.6");
  private static final OID IF_NAME_BASE = new OID("1.3.6.1.2.1.31.1.1.1.1");
  private static final OID IF_DESC_BASE = new OID("1.3.6.1.2.1.2.2.1.2");
  private static final OID IF_ADMIN_STATUS_BASE = new OID("1.3.6.1.2.1.2.2.1.7");
  private static final OID IF_OPER_STATUS_BASE = new OID("1.3.6.1.2.1.2.2.1.8");
  private static final OID IF_SPEED_BASE = new OID("1.3.6.1.2.1.2.2.1.5");
  private static final OID IF_ALIAS_BASE = new OID("1.3.6.1.2.1.31.1.1.1.18");
  private static final OID SYS_SERVICES = new OID("1.3.6.1.2.1.1.7.0");
  private static final OID IP_FORWARDING = new OID("1.3.6.1.2.1.4.1.0");
  private static final OID SYS_UPTIME = new OID("1.3.6.1.2.1.1.3.0");
  private static final OID SYS_CONTACT = new OID("1.3.6.1.2.1.1.4.0");
  private static final OID SYS_LOCATION = new OID("1.3.6.1.2.1.1.6.0");
  private static final OID ENT_PHYSICAL_HARDWARE_REV = new OID("1.3.6.1.2.1.47.1.1.1.1.8.1");
  private static final OID ENT_PHYSICAL_SOFTWARE_REV = new OID("1.3.6.1.2.1.47.1.1.1.1.10.1");
  private static final Map<String, String> ENTERPRISE_VENDORS = createEnterpriseVendors();
  private static final Map<String, String> WALK_OID_FIELD_ALIASES = createWalkOidFieldAliases();
  private static final List<String> CPU_HINTS = List.of("cpu", "load", "laload");
  private static final List<String> RAM_HINTS = List.of("vm.memory", "memory", "mem", "ram");
  private static final List<String> ROM_HINTS = List.of("vfs.fs", "filesystem", "disk", "storage", "rom", "hdd", "ssd");

  private final IpRangeParser rangeParser;
  private final AccessProfileResolver accessProfileResolver;
  private final DependentItemSnapshotPreprocessor dependentItemSnapshotPreprocessor;
  private final MonitoringPreprocessingEngine monitoringPreprocessingEngine;
  private final JsPreprocessingCompatService jsPreprocessingCompatService;
  private final ScanSubnetExecutionPools subnetExecutionPools;
  private final ReverseDnsLookupService reverseDnsLookupService;
  private final int subnetScanInvokeBatchSize;
  private final ConcurrentHashMap<Long, ActiveScanRun> activeRuns = new ConcurrentHashMap<>();

  private record ActiveScanRun(
      ScanRunContext context,
      AtomicReference<Thread> coordinator,
      Set<Future<DeviceScanResult>> tasks
  ) {}
  private static boolean snmpV3SecurityPrepared;
  private static final ConcurrentHashMap<String, CachedV3Engine> V3_ENGINE_CACHE = new ConcurrentHashMap<>();
  private static final long V3_ENGINE_CACHE_TTL_MS = 300_000L;

  private record CachedV3Engine(byte[] engineId, long expiresAtEpochMs) {}

  public SnmpScanServiceImpl(
      IpRangeParser rangeParser,
      AccessProfileResolver accessProfileResolver,
      DependentItemSnapshotPreprocessor dependentItemSnapshotPreprocessor,
      MonitoringPreprocessingEngine monitoringPreprocessingEngine,
      JsPreprocessingCompatService jsPreprocessingCompatService,
      ScanSubnetExecutionPools subnetExecutionPools,
      ReverseDnsLookupService reverseDnsLookupService,
      @Value("${network.scan.invoke-batch-size:256}") int subnetScanInvokeBatchSize
  ) {
    this.rangeParser = rangeParser;
    this.accessProfileResolver = accessProfileResolver;
    this.dependentItemSnapshotPreprocessor = dependentItemSnapshotPreprocessor;
    this.monitoringPreprocessingEngine = monitoringPreprocessingEngine;
    this.jsPreprocessingCompatService = jsPreprocessingCompatService;
    this.subnetExecutionPools = subnetExecutionPools;
    this.reverseDnsLookupService = reverseDnsLookupService;
    this.subnetScanInvokeBatchSize = Math.max(subnetScanInvokeBatchSize, 1);
  }

  @Override
  public ScanExecutionResult scan(ScanRequest request, ScanRunContext context) {
    ScanRequest effectiveRequest = accessProfileResolver.resolveScanRequest(request);
    List<DeviceScanResult> results = new ArrayList<>();
    List<String> addresses = rangeParser.expandRange(effectiveRequest.subnetRange());
    if (addresses.isEmpty()) {
      return new ScanExecutionResult(List.of(), false);
    }
    int totalAddresses = addresses.size();
    ActiveScanRun activeRun = new ActiveScanRun(
        context,
        new AtomicReference<>(Thread.currentThread()),
        ConcurrentHashMap.newKeySet()
    );
    activeRuns.put(context.runId(), activeRun);
    Semaphore concurrencySemaphore = subnetExecutionPools.concurrencySemaphore(context.source());
    ThreadPoolTaskExecutor subnetScanTaskExecutor = subnetExecutionPools.taskExecutor(context.source());
    try {
      concurrencySemaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Сканирование было прервано.", e);
    }
    try {
      ThreadPoolExecutor executor = subnetScanTaskExecutor.getThreadPoolExecutor();
      if (executor == null) {
        throw new IllegalStateException("Пул subnet-scan не инициализирован.");
      }
      results = new ArrayList<>(addresses.size());
      int scanned = 0;
      for (int offset = 0; offset < addresses.size(); offset += subnetScanInvokeBatchSize) {
        if (context.isStopRequested()) {
          break;
        }
        int end = Math.min(offset + subnetScanInvokeBatchSize, addresses.size());
        List<String> slice = addresses.subList(offset, end);
        List<Future<DeviceScanResult>> futures = slice.stream()
            .map(ip -> executor.submit(() -> {
              if (context.isStopRequested()) {
                return null;
              }
              return scanAddress(ip, effectiveRequest, context);
            }))
            .toList();
        activeRun.tasks().addAll(futures);
        for (Future<DeviceScanResult> future : futures) {
          try {
            if (context.isStopRequested()) {
              future.cancel(true);
              continue;
            }
            DeviceScanResult result = future.get();
            if (result != null) {
              results.add(result);
            }
          } catch (CancellationException e) {
            // Task was cancelled because stop was requested.
          } catch (ExecutionException e) {
            // Ignore host-level failures and continue scanning remaining addresses.
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (context.isStopRequested()) {
              cancelFutures(futures);
              context.progressListener().onProgress(scanned, totalAddresses);
              return new ScanExecutionResult(results, true);
            }
            throw e;
          } finally {
            scanned++;
            context.progressListener().onProgress(scanned, totalAddresses);
            activeRun.tasks().remove(future);
          }
        }
      }
      return new ScanExecutionResult(results, context.isStopRequested());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (context.isStopRequested()) {
        return new ScanExecutionResult(results, true);
      }
      throw new IllegalStateException("Сканирование было прервано.", e);
    } finally {
      activeRuns.remove(context.runId());
      activeRun.coordinator().set(null);
      concurrencySemaphore.release();
    }
  }

  @Override
  public boolean stopScan(long runId) {
    ActiveScanRun activeRun = activeRuns.get(runId);
    if (activeRun == null) {
      return false;
    }
    activeRun.context().stopRequested().set(true);
    cancelFutures(activeRun.tasks());
    Thread coordinator = activeRun.coordinator().get();
    if (coordinator != null) {
      coordinator.interrupt();
    }
    return true;
  }

  @Override
  public boolean checkIcmpReachable(String ip, int timeout) {
    return isReachable(ip, timeout);
  }

  @Override
  public boolean checkPortReachable(String ip, int port, int timeout) {
    return isPortOpen(ip, port, timeout);
  }

  @Override
  public boolean checkSnmpReachable(String ip, int port, int timeout, int retries, String community) {
    return checkSnmpReachable(ip, legacyMonitoringTemplate(port, timeout, retries, community));
  }

  @Override
  public List<DeviceInterfaceDto> readInterfaces(String ip, int port, int timeout, int retries, String community) {
    return readInterfaces(ip, legacyMonitoringTemplate(port, timeout, retries, community));
  }

  @Override
  public boolean checkSnmpReachable(String ip, ResolvedMonitoringTemplate template) {
    Map<String, String> discovery = template.oids().discovery();
    String probeOid = firstNonBlank(discovery.get("sysName"), SYS_NAME.toDottedString());
    return !readOidValues(ip, template, Map.of("probe", probeOid)).isEmpty();
  }

  @Override
  public List<DeviceInterfaceDto> readInterfaces(String ip, ResolvedMonitoringTemplate template) {
    Map<String, String> interfaceOids = template.oids().interfaces();
    Map<Integer, InterfaceAccumulator> interfaces = new TreeMap<>();
    try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
         Snmp snmp = new Snmp(transport)) {
      transport.listen();
      Target<Address> target = buildSnmpTarget(snmp, ip, template.snmp());
      walkColumn(
          snmp,
          target,
          new OID(oid(interfaceOids, "ifName", IF_NAME_BASE.toDottedString())),
          (index, value) -> accumulator(interfaces, index).name = value
      );
      walkColumn(
          snmp,
          target,
          new OID(oid(interfaceOids, "ifDescr", IF_DESC_BASE.toDottedString())),
          (index, value) -> accumulator(interfaces, index).description = value
      );
      walkColumn(
          snmp,
          target,
          new OID(oid(interfaceOids, "ifAlias", IF_ALIAS_BASE.toDottedString())),
          (index, value) -> accumulator(interfaces, index).alias = value
      );
      walkColumn(
          snmp,
          target,
          new OID(oid(interfaceOids, "ifAdminStatus", IF_ADMIN_STATUS_BASE.toDottedString())),
          (index, value) -> accumulator(interfaces, index).adminStatus = toStatus(value)
      );
      walkColumn(
          snmp,
          target,
          new OID(oid(interfaceOids, "ifOperStatus", IF_OPER_STATUS_BASE.toDottedString())),
          (index, value) -> accumulator(interfaces, index).operStatus = toStatus(value)
      );
      walkColumn(
          snmp,
          target,
          new OID(oid(interfaceOids, "ifSpeed", IF_SPEED_BASE.toDottedString())),
          (index, value) -> accumulator(interfaces, index).nominalSpeed = toSpeed(value)
      );
    } catch (IOException exception) {
      return List.of();
    }

    return interfaces.values().stream()
        .map(this::toInterfaceDto)
        .toList();
  }

  @Override
  public MonitoringDetailsDto readMonitoringDetails(String ip, int port, int timeout, int retries, String community) {
    return readMonitoringDetails(ip, legacyMonitoringTemplate(port, timeout, retries, community));
  }

  @Override
  public MonitoringDetailsDto readMonitoringDetails(String ip, ResolvedMonitoringTemplate template) {
    Map<String, String> detailsOids = template.oids().details();
    Map<String, String> requestedOids = new LinkedHashMap<>();
    requestedOids.put("sysDescr", oid(detailsOids, "sysDescr", SYS_DESCR.toDottedString()));
    requestedOids.put("uptime", oid(detailsOids, "uptime", SYS_UPTIME.toDottedString()));
    requestedOids.put("adminContact", oid(detailsOids, "adminContact", SYS_CONTACT.toDottedString()));
    requestedOids.put("location", oid(detailsOids, "location", SYS_LOCATION.toDottedString()));
    requestedOids.put("hardwareVersion", oid(detailsOids, "hardwareVersion", ENT_PHYSICAL_HARDWARE_REV.toDottedString()));
    requestedOids.put("bootVersion", oid(detailsOids, "bootVersion", ENT_PHYSICAL_SOFTWARE_REV.toDottedString()));

    Map<String, String> values = readOidValues(ip, template, requestedOids);
    Map<String, Double> dynamicMetrics = readMonitoringMetrics(ip, template);
    Map<String, ZabbixItemRuntime> metricDefinitions = template.items() == null ? Map.of() : template.items();

    TelemetrySnapshot telemetry = resolveTelemetrySnapshot(dynamicMetrics, metricDefinitions);

    return new MonitoringDetailsDto(
        new MonitoringMetricDto(
            telemetry.cpuCurrent(),
            telemetry.cpuAverage(),
            telemetry.cpuPeak(),
            telemetry.cpuCurrentItemName(),
            telemetry.cpuAverageItemName(),
            telemetry.cpuPeakItemName()
        ),
        telemetry.ramUsedPercent(),
        telemetry.romUsedPercent(),
        formatUptime(values.get("uptime")),
        firstNonBlank(values.get("sysDescr"), "-"),
        firstNonBlank(values.get("adminContact"), "-"),
        firstNonBlank(values.get("hardwareVersion"), "-"),
        firstNonBlank(values.get("location"), "-"),
        "-",
        firstNonBlank(values.get("bootVersion"), "-"),
        null,
        "DIRECT_SNMP",
        false
    );
  }

  @Override
  public Map<String, Double> readMonitoringMetrics(String ip, ResolvedMonitoringTemplate template) {
    Map<String, ZabbixItemRuntime> metricDefinitions = template.items();
    if (metricDefinitions == null || metricDefinitions.isEmpty()) {
      return Map.of();
    }

    Map<String, String> allOidRequests = new LinkedHashMap<>();
    for (Map.Entry<String, ZabbixItemRuntime> entry : metricDefinitions.entrySet()) {
      ZabbixItemRuntime definition = entry.getValue();
      if (definition.discoveryPrototype()
          || definition.isTextual()
          || !definition.isSnmpBased()
          || definition.isZabbixIcmpSimpleItem()
          || definition.snmpOid() == null
          || definition.snmpOid().isBlank()) {
        continue;
      }
      allOidRequests.put(entry.getKey(), definition.snmpOid());
    }

    Map<String, String> rawValues = readOidValues(ip, template, allOidRequests);
    Map<String, Double> metrics = new LinkedHashMap<>();

    for (Map.Entry<String, ZabbixItemRuntime> entry : metricDefinitions.entrySet()) {
      String metricKey = entry.getKey();
      ZabbixItemRuntime definition = entry.getValue();
      if (definition.discoveryPrototype()
          || definition.isTextual()
          || !definition.isSnmpBased()
          || definition.isZabbixIcmpSimpleItem()
          || definition.snmpOid() == null
          || definition.snmpOid().isBlank()) {
        continue;
      }
      Double value = applyRuntimePreprocessing(definition, rawValues.get(metricKey));
      if (value != null) {
        metrics.put(metricKey, value);
      }
    }

    appendDependentCpuLoadMetrics(ip, template, metrics, metricDefinitions);

    return metrics;
  }

  @Override
  public ItemStateTelemetrySnapshot resolveTelemetryFromItemValues(
      Map<String, Double> itemValues,
      Map<String, ZabbixItemRuntime> itemDefinitions
  ) {
    Map<String, Double> safeValues = itemValues == null ? Map.of() : itemValues;
    Map<String, ZabbixItemRuntime> safeDefinitions = itemDefinitions == null ? Map.of() : itemDefinitions;
    if (safeValues.isEmpty()) {
      return new ItemStateTelemetrySnapshot(
          new MonitoringMetricDto(null, null, null, null, null, null),
          null,
          null
      );
    }
    TelemetrySnapshot snapshot = resolveTelemetrySnapshot(safeValues, safeDefinitions);
    return new ItemStateTelemetrySnapshot(
        new MonitoringMetricDto(
            snapshot.cpuCurrent(),
            snapshot.cpuAverage(),
            snapshot.cpuPeak(),
            snapshot.cpuCurrentItemName(),
            snapshot.cpuAverageItemName(),
            snapshot.cpuPeakItemName()
        ),
        snapshot.ramUsedPercent(),
        snapshot.romUsedPercent()
    );
  }

  /**
   * Linux (UCD-SNMP) и аналогичные шаблоны: {@code system.cpu.load.avg*} — dependent от walk JSON;
   * без этого в snapshot попадают сторонние «cpu»-метрики (счётчики и т.д.).
   */
  private void appendDependentCpuLoadMetrics(
      String ip,
      ResolvedMonitoringTemplate template,
      Map<String, Double> metrics,
      Map<String, ZabbixItemRuntime> definitions
  ) {
    if (dependentItemSnapshotPreprocessor == null || definitions == null || definitions.isEmpty()) {
      return;
    }
    ZabbixItemRuntime walkMaster = definitions.get("system.cpu.load.walk");
    if (walkMaster == null || walkMaster.snmpOid() == null || walkMaster.snmpOid().isBlank()) {
      return;
    }
    String walkKey = walkMaster.key();
    if (walkKey == null || walkKey.isBlank()) {
      return;
    }
    List<ZabbixItemRuntime> dependents = definitions.values().stream()
        .filter(ZabbixItemRuntime::isDependent)
        .filter(r -> walkKey.equals(r.masterItemKey()))
        .filter(r -> r.key() != null && r.key().startsWith("system.cpu.load.avg"))
        .toList();
    if (dependents.isEmpty()) {
      return;
    }
    Map<String, String> raw = readOidValues(ip, template, Map.of(walkKey, walkMaster.snmpOid()));
    String walkPayload = raw.get(walkKey);
    if (walkPayload == null || walkPayload.isBlank()) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now();
    for (ZabbixItemRuntime dep : dependents) {
      if (dep.key() == null || dep.key().isBlank()) {
        continue;
      }
      Double v = dependentItemSnapshotPreprocessor.preprocessDependentNumeric(
          dep,
          walkPayload,
          now,
          MonitoringPreprocessContext.NONE
      );
      if (v == null) {
        continue;
      }
      metrics.put(dep.key(), v);
    }
  }

  @Override
  public Map<String, String> readRawOids(String ip, ResolvedMonitoringTemplate template, Map<String, String> requestedOids) {
    return readOidValues(ip, template, requestedOids);
  }

  @Override
  public List<DiscoveryInstanceRuntime> executeDiscovery(
      String ip,
      ResolvedMonitoringTemplate template,
      ZabbixDiscoveryRuleRuntime discoveryRule,
      OffsetDateTime timestamp
  ) {
    if (discoveryRule.isDependent()) {
      return executeDependentDiscovery(ip, template, discoveryRule, timestamp);
    }
    List<DiscoveryColumnSpec> columns = resolveDiscoveryColumns(parseDiscoveryColumns(discoveryRule.snmpOid()));
    if (columns.isEmpty()) {
      if (discoveryRule.snmpOid() != null && discoveryRule.snmpOid().contains("discovery[")) {
        log.debug("SNMP discovery rule '{}' has no supported discovery columns.", discoveryRule.key());
      }
      return List.of();
    }

    DiscoveryColumnSpec anchorColumn = columns.get(0);
    Map<String, String> anchorValues = walkOidValues(ip, template, new OID(anchorColumn.oid()));
    if (anchorValues.isEmpty()) {
      return List.of();
    }

    Map<String, String> requestedOids = new LinkedHashMap<>();
    for (String suffix : anchorValues.keySet()) {
      for (int i = 1; i < columns.size(); i++) {
        DiscoveryColumnSpec column = columns.get(i);
        requestedOids.put(discoveryColumnValueKey(suffix, column.macro()), column.oid() + "." + suffix);
      }
    }
    Map<String, String> supplementalValues = requestedOids.isEmpty()
        ? Map.of()
        : readOidValues(ip, template, requestedOids);

    List<DiscoveryInstanceRuntime> instances = new ArrayList<>();
    for (Map.Entry<String, String> entry : anchorValues.entrySet()) {
      String suffix = entry.getKey();
      Map<String, String> macros = new LinkedHashMap<>();
      macros.put("{#SNMPINDEX}", suffix);
      macros.put(anchorColumn.macro(), entry.getValue());
      for (int i = 1; i < columns.size(); i++) {
        DiscoveryColumnSpec column = columns.get(i);
        String value = supplementalValues.get(discoveryColumnValueKey(suffix, column.macro()));
        if (value != null) {
          macros.put(column.macro(), value);
        }
      }
      if (!matchesDiscoveryFilter(discoveryRule.filter(), macros)) {
        continue;
      }
      instances.add(new DiscoveryInstanceRuntime(
          discoveryRule.key(),
          suffix,
          Map.copyOf(macros),
          timestamp,
          timestamp.plusSeconds(Math.max(discoveryRule.lifetimeSeconds(), 1))
      ));
    }
    return List.copyOf(instances);
  }

  private List<DiscoveryInstanceRuntime> executeDependentDiscovery(
      String ip,
      ResolvedMonitoringTemplate template,
      ZabbixDiscoveryRuleRuntime discoveryRule,
      OffsetDateTime timestamp
  ) {
    if (discoveryRule.masterItemKey() == null || discoveryRule.masterItemKey().isBlank()) {
      return List.of();
    }
    ZabbixItemRuntime masterItem = template.items().get(discoveryRule.masterItemKey());
    if (masterItem == null) {
      return List.of();
    }
    String payload = resolveLldMasterPayload(ip, template, masterItem, timestamp);
    if (payload == null || payload.isBlank()) {
      return List.of();
    }
    try {
      String processedPayload = applyDependentDiscoveryPreprocessing(payload, discoveryRule, masterItem);
      JsonNode root = OBJECT_MAPPER.readTree(processedPayload);
      if (!root.isArray()) {
        return List.of();
      }
      List<DiscoveryInstanceRuntime> instances = new ArrayList<>();
      int ordinal = 0;
      for (JsonNode row : root) {
        if (!row.isObject()) {
          continue;
        }
        Map<String, String> macros = mapLldMacros(row, discoveryRule.lldMacroPaths());
        if (macros.isEmpty()) {
          continue;
        }
        if (!matchesDiscoveryFilter(discoveryRule.filter(), macros)) {
          continue;
        }
        String instanceKey = firstNonBlank(macros.get("{#SNMPINDEX}"), String.valueOf(++ordinal));
        instances.add(new DiscoveryInstanceRuntime(
            discoveryRule.key(),
            instanceKey,
            Map.copyOf(macros),
            timestamp,
            timestamp.plusSeconds(Math.max(discoveryRule.lifetimeSeconds(), 1))
        ));
      }
      return List.copyOf(instances);
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }

  /**
   * Значение master-item для DEPENDENT LLD: либо прямой SNMP (walk/get), либо цепочка dependent → сырой walk.
   */
  private String resolveLldMasterPayload(
      String ip,
      ResolvedMonitoringTemplate template,
      ZabbixItemRuntime item,
      OffsetDateTime timestamp
  ) {
    if (item == null) {
      return null;
    }
    if (item.snmpOid() != null && !item.snmpOid().isBlank()) {
      return readOidValues(ip, template, Map.of(item.key(), item.snmpOid())).get(item.key());
    }
    if (!item.isDependent()) {
      return null;
    }
    String parentKey = item.masterItemKey();
    if (parentKey == null || parentKey.isBlank()) {
      return null;
    }
    ZabbixItemRuntime parent = template.items().get(parentKey);
    String parentRaw = resolveLldMasterPayload(ip, template, parent, timestamp);
    if (parentRaw == null || parentRaw.isBlank()) {
      return null;
    }
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        template.id(),
        item,
        item.key(),
        item.key(),
        "",
        null,
        parent != null ? parent.snmpOid() : null,
        Map.of());
    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = monitoringPreprocessingEngine.process(
        item,
        parentRaw,
        null,
        timestamp,
        new MonitoringPreprocessContext(template, materialized));
    if (processed.discarded()) {
      return null;
    }
    if (processed.textValue() != null && !processed.textValue().isBlank()) {
      return processed.textValue();
    }
    if (processed.numericValue() != null) {
      return String.valueOf(processed.numericValue());
    }
    return null;
  }

  /**
   * Как в Zabbix: для DEPENDENT discovery с шагом SNMP_WALK_TO_JSON превращаем сырой walk JSON (index, colN / алиасы)
   * в массив объектов с ключами LLD-макросов.
   */
  private String applyDependentDiscoveryPreprocessing(
      String masterPayload,
      ZabbixDiscoveryRuleRuntime discoveryRule,
      ZabbixItemRuntime masterItem
  ) throws JsonProcessingException {
    String current = masterPayload;
    List<ZabbixPreprocessingStep> steps = discoveryRule.preprocessing();
    if (steps == null || steps.isEmpty()) {
      return current;
    }
    for (ZabbixPreprocessingStep step : steps) {
      if (step == null || step.type() == null) {
        continue;
      }
      String type = step.type().trim().toUpperCase();
      if ("SNMP_WALK_TO_JSON".equals(type)) {
        current = applySnmpWalkToJsonToWalkPayload(current, step, masterItem);
      } else if ("DISCARD_UNCHANGED_HEARTBEAT".equals(type)) {
        // Без истории LLD шаг не применим — оставляем значение как есть.
      } else if ("JAVASCRIPT".equals(type)) {
        String script = step.parameters() == null || step.parameters().isEmpty()
            ? ""
            : (step.parameters().get(0) == null ? "" : step.parameters().get(0));
        JsPreprocessingCompatService.JsResult js = jsPreprocessingCompatService.execute(current, script, Map.of());
        if ("ok".equals(js.status()) && js.value() != null) {
          current = js.value();
        } else {
          log.debug("Dependent discovery '{}': JAVASCRIPT step status={} note={}",
              discoveryRule.key(), js.status(), js.note());
        }
      } else {
        log.debug("Dependent discovery '{}': unsupported preprocessing step {}, skipped.", discoveryRule.key(), type);
      }
    }
    return current;
  }

  private String applySnmpWalkToJsonToWalkPayload(
      String walkJson,
      ZabbixPreprocessingStep step,
      ZabbixItemRuntime masterItem
  ) throws JsonProcessingException {
    List<String> columnOids = parseWalkColumns(masterItem.snmpOid());
    if (columnOids.isEmpty()) {
      return walkJson;
    }
    List<String[]> triplets = SnmpWalkJsonSupport.parseSnmpWalkToJsonTriplets(step.parameters());
    if (triplets.isEmpty()) {
      return walkJson;
    }
    JsonNode root = OBJECT_MAPPER.readTree(walkJson);
    if (!root.isArray()) {
      return walkJson;
    }
    ArrayNode out = OBJECT_MAPPER.createArrayNode();
    for (JsonNode row : root) {
      if (!row.isObject()) {
        continue;
      }
      ObjectNode obj = OBJECT_MAPPER.createObjectNode();
      JsonNode indexNode = row.get("index");
      if (indexNode != null && !indexNode.isNull()) {
        obj.put("{#SNMPINDEX}", indexNode.isValueNode() ? indexNode.asText() : indexNode.toString());
      }
      for (String[] triplet : triplets) {
        String macroKey = triplet[0];
        String oid = triplet[1];
        String defaultValue = triplet[2];
        String field = fieldNameForWalkColumnOid(columnOids, oid);
        String cell = field == null ? null : SnmpWalkJsonSupport.jsonCellToString(row.get(field));
        if (cell == null) {
          cell = defaultValue;
        }
        if (macroKey != null && !macroKey.isBlank()) {
          obj.put(macroKey, cell == null ? "" : cell);
        }
      }
      out.add(obj);
    }
    return OBJECT_MAPPER.writeValueAsString(out);
  }

  private String fieldNameForWalkColumnOid(List<String> columnOids, String targetOid) {
    if (targetOid == null || targetOid.isBlank()) {
      return null;
    }
    String normalized = targetOid.trim();
    for (int i = 0; i < columnOids.size(); i++) {
      if (normalized.equals(columnOids.get(i))) {
        return aliasForWalkOid(columnOids.get(i), i);
      }
    }
    return null;
  }

  private Map<String, String> mapLldMacros(JsonNode row, List<com.networkscanner.backend.monitoring.dto.ZabbixLldMacroPathRecord> macroPaths) {
    if (macroPaths == null || macroPaths.isEmpty()) {
      return macrosFromLldJsonRowWhenPathsEmpty(row);
    }
    Map<String, String> macros = new LinkedHashMap<>();
    for (com.networkscanner.backend.monitoring.dto.ZabbixLldMacroPathRecord macroPath : macroPaths) {
      if (macroPath == null || macroPath.lldMacro() == null || macroPath.path() == null) {
        continue;
      }
      String value = readSimpleJsonPath(row, macroPath.path());
      if (value != null) {
        macros.put(macroPath.lldMacro(), value);
      }
    }
    return macros;
  }

  /**
   * После SNMP_WALK_TO_JSON строки discovery — объекты с ключами вида {@code {#IFNAME}} без lld_macro_paths в шаблоне.
   */
  private Map<String, String> macrosFromLldJsonRowWhenPathsEmpty(JsonNode row) {
    Map<String, String> macros = new LinkedHashMap<>();
    row.fields().forEachRemaining(entry -> {
      String key = entry.getKey();
      if (key != null && key.startsWith("{#") && key.endsWith("}") && key.length() > 3) {
        String v = SnmpWalkJsonSupport.jsonCellToString(entry.getValue());
        if (v != null) {
          macros.put(key, v);
        }
      }
    });
    if (!macros.containsKey("{#SNMPINDEX}")) {
      JsonNode indexNode = row.get("index");
      if (indexNode != null && !indexNode.isNull()) {
        macros.put("{#SNMPINDEX}", indexNode.isValueNode() ? indexNode.asText() : indexNode.toString());
      }
    }
    return macros;
  }

  private String readSimpleJsonPath(JsonNode row, String path) {
    String trimmed = path == null ? "" : path.trim();
    if (!trimmed.startsWith("$.")) {
      return null;
    }
    String field = trimmed.substring(2);
    if (field.isBlank()) {
      return null;
    }
    JsonNode node = row.get(field);
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    return node.isValueNode() ? node.asText() : node.toString();
  }

  private Double applyPreprocessing(String functionName, String metricKey,
      MetricDefinition definition, Map<String, String> rawValues) {
    if (functionName == null || "-".equals(functionName) || functionName.isBlank()) {
      String raw = definition.isSingleOid() ? rawValues.get(metricKey) : null;
      long parsed = parseLong(raw);
      return parsed != 0 ? (double) parsed : null;
    }

    return switch (functionName) {
      case "clamp_percent" -> {
        String raw = rawValues.get(metricKey);
        int val = clampPercent(parseInt(raw));
        yield val > 0 ? (double) val : null;
      }
      case "to_long" -> {
        String raw = rawValues.get(metricKey);
        long val = parseLong(raw);
        yield val != 0 ? (double) val : null;
      }
      case "usage_percent" -> {
        if (!definition.isSingleOid()) {
          Map<String, String> oidNames = definition.multiOid();
          String usedKey = metricKey + ".used";
          String freeKey = metricKey + ".free";
          if (!oidNames.containsKey("used") || !oidNames.containsKey("free")) {
            var keys = oidNames.keySet().iterator();
            usedKey = metricKey + "." + (keys.hasNext() ? keys.next() : "used");
            freeKey = metricKey + "." + (keys.hasNext() ? keys.next() : "free");
          }
          long used = parseLong(rawValues.get(usedKey));
          long free = parseLong(rawValues.get(freeKey));
          if (used + free > 0) {
            yield (double) computeUsagePercent(used, free);
          }
        }
        yield null;
      }
      default -> {
        String raw = definition.isSingleOid() ? rawValues.get(metricKey) : null;
        long parsed = parseLong(raw);
        yield parsed != 0 ? (double) parsed : null;
      }
    };
  }

  private Double applyRuntimePreprocessing(ZabbixItemRuntime definition, String rawValue) {
    if (definition == null || rawValue == null || rawValue.isBlank()) {
      return null;
    }
    double numericValue = parseLong(rawValue);
    if (definition.preprocessing() == null || definition.preprocessing().isEmpty()) {
      return numericValue;
    }
    for (var step : definition.preprocessing()) {
      String type = step.type() == null ? "" : step.type().trim().toUpperCase();
      String parameter = step.parameters() == null || step.parameters().isEmpty() ? "" : step.parameters().get(0);
      switch (type) {
        case "MULTIPLIER" -> numericValue = numericValue * parseDouble(parameter, 1.0);
        case "CHANGE_PER_SECOND", "SIMPLE_CHANGE" -> {
          // Runtime delta-based preprocessing is handled by the collector where history is available.
        }
        default -> {
          // Unknown step: keep current numeric value.
        }
      }
    }
    return numericValue;
  }

  private DeviceScanResult scanAddress(String ip, ScanRequest request, ScanRunContext context) {
    if (context.isStopRequested()) {
      return null;
    }

    List<DiscoveryProbeConfig> probes = request.effectiveProbes();
    if (probes.isEmpty()) {
      return null;
    }

    List<AvailabilityDto> availability = new ArrayList<>();
    boolean anySuccess = false;
    boolean icmpSuccess = false;
    String domainName = "-";
    Map<String, String> bestSnmpData = Map.of();
    int bestSnmpPriority = -1;
    Integer firstSuccessPort = null;
    List<String> successfulMethodLabels = new ArrayList<>();

    for (DiscoveryProbeConfig probe : probes) {
      String scanMode = normalizeScanMode(probe.method());
      int port = ScanRequest.resolveProbePort(probe);
      ProbeOutcome outcome = executeProbe(ip, request, probe, scanMode, port);
      anySuccess = anySuccess || outcome.success();
      if ("ICMP".equals(scanMode) && outcome.success()) {
        icmpSuccess = true;
      }
      if (outcome.domainName() != null && !"-".equals(outcome.domainName())) {
        domainName = outcome.domainName();
      }
      availability.add(new AvailabilityDto(
          availabilityLabel(scanMode),
          outcome.success(),
          outcome.success() ? "green" : "red"
      ));

      if (outcome.success()) {
        successfulMethodLabels.add(displayPollingStatus(scanMode));
        if (firstSuccessPort == null && usesProbePort(scanMode)) {
          firstSuccessPort = port;
        }
        if (outcome.snmpData() != null && !outcome.snmpData().isEmpty()) {
          int priority = snmpPriority(scanMode);
          if (priority > bestSnmpPriority) {
            bestSnmpPriority = priority;
            bestSnmpData = outcome.snmpData();
          }
        }
      }
    }

    if (!anySuccess) {
      return null;
    }

    boolean hasSnmp = !bestSnmpData.isEmpty();
    String sysName = firstNonBlank(bestSnmpData.get("sysName"), "-");
    String sysDescr = firstNonBlank(bestSnmpData.get("sysDescr"), "-");
    String sysObjectId = firstNonBlank(bestSnmpData.get("sysObjectId"), "-");
    String serial = presentableScalar(bestSnmpData.get("serialNumber"), "-");
    String model = presentableScalar(bestSnmpData.get("model"), inferModel(sysDescr));
    String vendor = inferVendor(sysObjectId, sysDescr);
    String firmware = inferFirmware(sysDescr);

    String resultName = hasSnmp ? sysDescr : "-";
    if ("-".equals(domainName) && icmpSuccess) {
      domainName = reverseDnsLookupService.lookup(ip);
    }

    List<String> tags = hasSnmp ? SnmpDeviceTypeClassifier.resolveTags(bestSnmpData) : List.of();

    return new DeviceScanResult(
        hasSnmp ? sysName : "-",
        resultName,
        hasSnmp ? serial : "-",
        ip,
        domainName,
        hasSnmp ? presentableScalar(bestSnmpData.get("macAddress"), "-") : "-",
        hasSnmp ? vendor : "-",
        hasSnmp ? model : "-",
        hasSnmp ? firmware : "-",
        String.join(", ", successfulMethodLabels),
        "Включено",
        "-",
        tags,
        availability,
        firstSuccessPort,
        null
    );
  }

  private record ProbeOutcome(boolean success, Map<String, String> snmpData, String domainName) {

    private ProbeOutcome(boolean success, Map<String, String> snmpData) {
      this(success, snmpData, null);
    }
  }

  private ProbeOutcome executeProbe(
      String ip,
      ScanRequest request,
      DiscoveryProbeConfig probe,
      String scanMode,
      int port
  ) {
    if ("ICMP".equals(scanMode)) {
      return new ProbeOutcome(isReachable(ip, request.timeout()), null);
    }
    if ("DNS".equals(scanMode)) {
      String resolvedDomain = reverseDnsLookupService.lookup(ip);
      boolean success = !"-".equals(resolvedDomain);
      return new ProbeOutcome(success, null, success ? resolvedDomain : null);
    }
    if (isSnmpMode(scanMode)) {
      Map<String, String> snmpData = readSnmpForProbe(ip, request, probe, scanMode);
      return new ProbeOutcome(!snmpData.isEmpty(), snmpData);
    }
    if (isHttpMode(scanMode)) {
      return new ProbeOutcome(isHttpReachable(ip, scanMode, port, request.timeout(), probe), null);
    }
    if ("SSH".equals(scanMode) && hasSshCredentials(probe)) {
      return new ProbeOutcome(isSshReachable(ip, port, probe, request.timeout()), null);
    }
    if (isPortBasedMode(scanMode)) {
      return new ProbeOutcome(isPortOpen(ip, port, request.timeout()), null);
    }
    return new ProbeOutcome(false, null);
  }

  private int snmpPriority(String scanMode) {
    return switch (scanMode) {
      case "SNMP_V3" -> 3;
      case "SNMP_V2" -> 2;
      case "SNMP_V1" -> 1;
      default -> 0;
    };
  }

  private String availabilityLabel(String scanMode) {
    if ("ICMP".equals(scanMode)) {
      return "ICMP";
    }
    if ("DNS".equals(scanMode)) {
      return "DNS";
    }
    if (isSnmpMode(scanMode)) {
      return displayPollingStatus(scanMode);
    }
    return scanMode;
  }

  private void cancelFutures(Collection<? extends Future<?>> futures) {
    for (Future<?> future : futures) {
      future.cancel(true);
    }
  }

  private boolean isReachable(String ip, int timeout) {
    try {
      return InetAddress.getByName(ip).isReachable(timeout);
    } catch (IOException e) {
      return false;
    }
  }

  private Map<String, String> readSnmpForProbe(
      String ip,
      ScanRequest request,
      DiscoveryProbeConfig probe,
      String scanMode
  ) {
    if ("SNMP_V3".equals(scanMode)) {
      return readSnmpV3ForProbe(ip, request, probe);
    }

    Map<String, String> values = new LinkedHashMap<>();
    try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
         Snmp snmp = new Snmp(transport)) {
      transport.listen();

      Target<Address> target = buildTargetForProbe(ip, request, probe, scanMode);
      PDU pdu = buildPdu(scanMode);
      ResponseEvent<Address> event = snmp.send(pdu, target);
      if (event == null || event.getResponse() == null) {
        return values;
      }

      mergeSnmpGetResponse(event.getResponse(), values);
      enrichSnmpDiscoveryFields(snmp, target, values);
    } catch (IOException e) {
      return Map.of();
    }
    return filterPresentSnmpValues(values);
  }

  private Map<String, String> readSnmpV3ForProbe(String ip, ScanRequest request, DiscoveryProbeConfig probe) {
    Map<String, String> values = new LinkedHashMap<>();
    int port = ScanRequest.resolveProbePort(probe);
    try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
         Snmp snmp = new Snmp(transport)) {
      ensureUsmRegistered();
      transport.listen();

      Address targetAddress = GenericAddress.parse("udp:" + ip + "/" + port);
      byte[] engineId = snmp.discoverAuthoritativeEngineID(targetAddress, request.timeout());
      if (engineId == null || engineId.length == 0) {
        return values;
      }

      String authPassword = probe.authPassword();
      String privacyPassword = probe.privacyPassword();
      if (authPassword == null || authPassword.isBlank() || privacyPassword == null || privacyPassword.isBlank()) {
        return values;
      }

      OctetString securityName = new OctetString(firstNonBlank(probe.securityUsername(), ""));
      byte[] authoritativeEngineId = Arrays.copyOf(engineId, engineId.length);
      OctetString engineIdOctet = new OctetString();
      engineIdOctet.setValue(authoritativeEngineId);
      snmp.getUSM().addUser(
          securityName,
          engineIdOctet,
          new UsmUser(
              securityName,
              resolveAuthProtocol(probe.authProtocol()),
              new OctetString(authPassword),
              resolvePrivProtocol(probe.privacyProtocol()),
              new OctetString(privacyPassword)
          )
      );

      UserTarget<Address> target = new UserTarget<>();
      target.setAddress(targetAddress);
      target.setRetries(request.retries());
      target.setTimeout(request.timeout());
      target.setVersion(SnmpConstants.version3);
      target.setSecurityLevel(resolveSecurityLevelForProbe(probe));
      target.setSecurityName(securityName);
      target.setAuthoritativeEngineID(authoritativeEngineId);

      PDU pdu = buildPdu("SNMP_V3");
      ResponseEvent<Address> event = snmp.send(pdu, target);
      if (event == null || event.getResponse() == null) {
        return values;
      }

      mergeSnmpGetResponse(event.getResponse(), values);
      enrichSnmpDiscoveryFields(snmp, target, values);
    } catch (IOException | RuntimeException e) {
      return Map.of();
    }
    return filterPresentSnmpValues(values);
  }

  private void enrichSnmpDiscoveryFields(Snmp snmp, Target<Address> target, Map<String, String> values) {
    try {
      if (!values.containsKey("macAddress")) {
        String mac = pickMacFromIfPhysAddressWalk(snmp, target);
        if (mac != null) {
          values.put("macAddress", mac);
        }
      }
      if (!values.containsKey("serialNumber")) {
        String serial = firstPresentableWalkScalar(snmp, target, ENT_PHYSICAL_SERIAL_COLUMN);
        if (serial != null) {
          values.put("serialNumber", serial);
        }
      }
      if (!values.containsKey("model")) {
        String model = firstPresentableWalkScalar(snmp, target, ENT_PHYSICAL_MODEL_COLUMN);
        if (model != null) {
          values.put("model", model);
        }
      }
    } catch (Exception ignored) {
      // Optional walks must not discard a successful GET.
    }
  }

  private void ensureUsmRegistered() {
    synchronized (SnmpScanServiceImpl.class) {
      if (snmpV3SecurityPrepared) {
        return;
      }
      SecurityProtocols securityProtocols = SecurityProtocols.getInstance();
      securityProtocols.addDefaultProtocols();
      securityProtocols.addAuthenticationProtocol(new AuthMD5());
      securityProtocols.addAuthenticationProtocol(new AuthSHA());
      securityProtocols.addPrivacyProtocol(new PrivDES());
      securityProtocols.addPrivacyProtocol(new PrivAES128());
      if (SecurityModels.getInstance().getSecurityModel(new Integer32(SecurityModel.SECURITY_MODEL_USM)) == null) {
        SecurityModels.getInstance().addSecurityModel(
            new USM(securityProtocols, new OctetString(MPv3.createLocalEngineID()), 0));
      }
      snmpV3SecurityPrepared = true;
    }
  }

  private void mergeSnmpGetResponse(PDU response, Map<String, String> values) {
    for (VariableBinding binding : response.getVariableBindings()) {
      String raw = binding.getVariable() == null ? null : binding.getVariable().toString();
      String value = sanitizeSnmpPresentation(raw);
      OID oid = binding.getOid();
      if (oid.startsWith(SYS_NAME) && value != null) {
        values.put("sysName", value);
      } else if (oid.startsWith(SYS_DESCR) && value != null) {
        values.put("sysDescr", value);
      } else if (oid.startsWith(SYS_OBJECT_ID) && value != null) {
        values.put("sysObjectId", value);
      } else if (oid.startsWith(BRIDGE_MAC) && value != null) {
        values.put("macAddress", value);
      } else if (oid.startsWith(ENT_PHYSICAL_SERIAL) && value != null) {
        values.put("serialNumber", value);
      } else if (oid.startsWith(ENT_PHYSICAL_MODEL) && value != null) {
        values.put("model", value);
      } else if (oid.startsWith(SYS_SERVICES) && value != null) {
        values.put("sysServices", value);
      } else if (oid.startsWith(IP_FORWARDING) && value != null) {
        values.put("ipForwarding", value);
      }
    }
  }

  private Map<String, String> filterPresentSnmpValues(Map<String, String> values) {
    return values.entrySet().stream()
        .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Target<Address> buildTargetForProbe(
      String ip,
      ScanRequest request,
      DiscoveryProbeConfig probe,
      String scanMode
  ) {
    int port = ScanRequest.resolveProbePort(probe);
    Address address = GenericAddress.parse("udp:" + ip + "/" + port);
    CommunityTarget<Address> target = new CommunityTarget<>();
    target.setAddress(address);
    target.setRetries(request.retries());
    target.setTimeout(request.timeout());
    target.setVersion("SNMP_V1".equals(scanMode) ? SnmpConstants.version1 : SnmpConstants.version2c);
    target.setCommunity(new OctetString(firstNonBlank(probe.community(), "public")));
    return target;
  }

  private PDU buildPdu(String scanMode) {
    PDU pdu = "SNMP_V3".equals(scanMode) ? new ScopedPDU() : new PDU();
    pdu.add(new VariableBinding(SYS_NAME));
    pdu.add(new VariableBinding(SYS_DESCR));
    pdu.add(new VariableBinding(SYS_OBJECT_ID));
    pdu.add(new VariableBinding(BRIDGE_MAC));
    pdu.add(new VariableBinding(ENT_PHYSICAL_SERIAL));
    pdu.add(new VariableBinding(ENT_PHYSICAL_MODEL));
    pdu.add(new VariableBinding(SYS_SERVICES));
    pdu.add(new VariableBinding(IP_FORWARDING));
    pdu.setType(PDU.GET);
    return pdu;
  }

  private int resolveSecurityLevelForProbe(DiscoveryProbeConfig probe) {
    boolean hasAuth = probe.authPassword() != null && !probe.authPassword().isBlank();
    boolean hasPriv = probe.privacyPassword() != null && !probe.privacyPassword().isBlank();
    if (hasAuth && hasPriv) {
      return SecurityLevel.AUTH_PRIV;
    }
    if (hasAuth) {
      return SecurityLevel.AUTH_NOPRIV;
    }
    return SecurityLevel.NOAUTH_NOPRIV;
  }

  private OID resolveAuthProtocol(String authProtocol) {
    if ("MD5".equalsIgnoreCase(authProtocol)) {
      return AuthMD5.ID;
    }
    return AuthSHA.ID;
  }

  private OID resolvePrivProtocol(String privProtocol) {
    if ("DES".equalsIgnoreCase(privProtocol)) {
      return PrivDES.ID;
    }
    return PrivAES128.ID;
  }

  private String inferVendor(String sysObjectId, String sysDescr) {
    String normalizedOid = normalizeOid(sysObjectId);
    if (normalizedOid != null) {
      for (Map.Entry<String, String> entry : ENTERPRISE_VENDORS.entrySet()) {
        if (normalizedOid.startsWith(entry.getKey())) {
          return entry.getValue();
        }
      }
    }

    String descr = firstNonBlank(sysDescr, "");
    String upper = descr.toUpperCase();
    if (upper.contains("H3C")) {
      return "H3C";
    }
    if (upper.contains("CISCO")) {
      return "Cisco";
    }
    if (upper.contains("MIKROTIK")) {
      return "MikroTik";
    }
    if (upper.contains("MES")) {
      return "MES";
    }
    if (upper.contains("JUNIPER")) {
      return "Juniper";
    }
    if (upper.contains("ARUBA")) {
      return "Aruba";
    }
    if (upper.contains("HP")) {
      return "HP";
    }
    if (upper.contains("NET-SNMP") || upper.contains("LINUX")) {
      return "Linux";
    }
    return "-";
  }

  private String inferModel(String sysDescr) {
    if (sysDescr == null || sysDescr.isBlank() || "-".equals(sysDescr)) {
      return "-";
    }
    String[] patterns = {
        "(?i)H3C\\s+([A-Z0-9-]+)",
        "(?i)Cisco\\s+.*?\\b([A-Z]{2,}[A-Z0-9-]+)\\b",
        "(?i)MES\\s*([0-9A-Z-]+)",
        "(?i)MikroTik\\s+([A-Z0-9-]+)",
        "(?i)Juniper\\s+Networks.*?\\b([A-Z]{2,}[A-Z0-9-]+)\\b"
    };

    for (String pattern : patterns) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(sysDescr);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }

    if (sysDescr.toUpperCase().contains("LINUX")) {
      java.util.regex.Matcher linuxMatcher = java.util.regex.Pattern
          .compile("(?i)Linux\\s+(\\S+)\\s+([0-9][0-9a-zA-Z.\\-+_]+)")
          .matcher(sysDescr);
      if (linuxMatcher.find()) {
        return "Linux (" + linuxMatcher.group(2) + ")";
      }
      return "Linux";
    }

    String[] tokens = sysDescr.split("[, ]+");
    return tokens.length > 0 ? tokens[0] : sysDescr;
  }

  private String inferFirmware(String sysDescr) {
    if (sysDescr == null || sysDescr.isBlank()) {
      return "-";
    }
    return sysDescr.length() > 80 ? sysDescr.substring(0, 80) : sysDescr;
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /**
   * SNMP error / empty bindings render as tokens like {@code noSuchObject}; treat as absent so UI and fallbacks apply.
   */
  static String sanitizeSnmpPresentation(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    if (t.isEmpty()) {
      return null;
    }
    String upper = t.toUpperCase();
    if (upper.equals("NOSUCHOBJECT")
        || upper.equals("NOSUCHINSTANCE")
        || upper.equals("ENDOFMIBVIEW")
        || upper.equals("NULL")) {
      return null;
    }
    return raw.trim();
  }

  private String presentableScalar(String raw, String fallback) {
    String s = sanitizeSnmpPresentation(raw);
    return s == null || s.isBlank() ? fallback : s;
  }

  private String firstPresentableWalkScalar(Snmp snmp, Target<Address> target, OID columnBase) {
    try {
      String[] holder = new String[1];
      walkColumnValues(snmp, target, columnBase, (suffix, raw) -> {
        if (holder[0] != null) {
          return;
        }
        String cleaned = sanitizeSnmpPresentation(raw);
        if (cleaned != null && !cleaned.isBlank()) {
          holder[0] = cleaned;
        }
      });
      return holder[0];
    } catch (IOException e) {
      return null;
    }
  }

  private String pickMacFromIfPhysAddressWalk(Snmp snmp, Target<Address> target) {
    try {
      List<Map.Entry<Integer, String>> candidates = new ArrayList<>();
      walkColumnValues(snmp, target, IF_PHYS_ADDRESS_BASE, (suffix, raw) -> {
        String cleaned = sanitizeSnmpPresentation(raw);
        if (cleaned == null) {
          return;
        }
        String mac = normalizeMacFromSnmp(cleaned);
        if (mac == null || isAllZeroMac(mac)) {
          return;
        }
        int ifIndex = parseLastSubIdentifier(suffix);
        if (ifIndex <= 0) {
          return;
        }
        candidates.add(Map.entry(ifIndex, mac));
      });
      candidates.sort(Comparator.comparingInt(Map.Entry::getKey));
      return candidates.stream()
          .filter(e -> e.getKey() != 1)
          .map(Map.Entry::getValue)
          .findFirst()
          .orElseGet(() -> candidates.isEmpty() ? null : candidates.get(0).getValue());
    } catch (IOException e) {
      return null;
    }
  }

  private static String normalizeMacFromSnmp(String raw) {
    String t = raw.trim();
    if (t.isEmpty()) {
      return null;
    }
    try {
      if (t.matches("^\\d+(\\.\\d+){5}$")) {
        String[] parts = t.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
          if (i > 0) {
            sb.append(':');
          }
          int b = Integer.parseInt(parts[i]);
          if (b < 0 || b > 255) {
            return null;
          }
          sb.append(String.format("%02x", b));
        }
        return sb.toString();
      }
      if (t.startsWith("0x") || t.startsWith("0X")) {
        String hex = t.substring(2).replaceAll("[^0-9a-fA-F]", "");
        if (hex.length() != 12) {
          return null;
        }
        return formatMacHex12(hex);
      }
      String hexOnly = t.replaceAll("[^0-9a-fA-F]", "");
      if (hexOnly.length() == 12 && !t.contains(":") && !t.contains("-")) {
        return formatMacHex12(hexOnly);
      }
      if (t.contains(":") || t.contains("-")) {
        String sep = t.contains(":") ? ":" : "-";
        String[] parts = t.split("[" + sep + "]");
        if (parts.length == 6) {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
              sb.append(':');
            }
            sb.append(String.format("%02x", Integer.parseInt(parts[i].trim(), 16)));
          }
          return sb.toString();
        }
      }
    } catch (NumberFormatException ignored) {
      return null;
    }
    return null;
  }

  private static String formatMacHex12(String hex12) {
    String lower = hex12.toLowerCase();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 12; i += 2) {
      if (i > 0) {
        sb.append(':');
      }
      sb.append(lower, i, i + 2);
    }
    return sb.toString();
  }

  private static boolean isAllZeroMac(String mac) {
    String hex = mac.replace(":", "");
    return hex.chars().allMatch(c -> c == '0');
  }

  private String oid(Map<String, String> oids, String key, String fallback) {
    if (oids == null) {
      return fallback;
    }
    return firstNonBlank(oids.get(key), fallback);
  }

  private String resolveOidExpression(String oid) {
    if (oid == null) {
      return null;
    }
    return oid
        .replace("SNMPv2-MIB::sysContact.0", "1.3.6.1.2.1.1.4.0")
        .replace("IF-MIB::ifHCInOctets", "1.3.6.1.2.1.31.1.1.1.6")
        .replace("IF-MIB::ifHCOutOctets", "1.3.6.1.2.1.31.1.1.1.10")
        .replace("IF-MIB::ifOutErrors", "1.3.6.1.2.1.2.2.1.20")
        .replace("IF-MIB::ifName", "1.3.6.1.2.1.31.1.1.1.1");
  }

  private Target<Address> buildSnmpTarget(Snmp snmp, String ip, MonitoringTemplateSnmp snmpConfig) throws IOException {
    MonitoringTemplateSnmp cfg = snmpConfig == null
        ? MonitoringTemplateSnmp.v2c("public", 3000, 1, 161)
        : snmpConfig;
    Address targetAddress = GenericAddress.parse("udp:" + ip + "/" + resolveSnmpPort(cfg));
    int timeout = cfg.timeoutMs() == null ? 3000 : cfg.timeoutMs();
    int retries = cfg.retries() == null ? 1 : cfg.retries();
    if (cfg.isV3()) {
      return buildV3UserTarget(snmp, targetAddress, cfg, timeout, retries);
    }
    CommunityTarget<Address> target = new CommunityTarget<>();
    target.setAddress(targetAddress);
    target.setRetries(retries);
    target.setTimeout(timeout);
    if ("v1".equalsIgnoreCase(cfg.version())) {
      target.setVersion(SnmpConstants.version1);
    } else {
      target.setVersion(SnmpConstants.version2c);
    }
    target.setCommunity(new OctetString(firstNonBlank(cfg.communityDefault(), "public")));
    return target;
  }

  private UserTarget<Address> buildV3UserTarget(
      Snmp snmp,
      Address targetAddress,
      MonitoringTemplateSnmp cfg,
      int timeout,
      int retries
  ) throws IOException {
    ensureUsmRegistered();
    byte[] engineId = resolveAuthoritativeEngineId(snmp, targetAddress, cfg, timeout);
    String securityName = cfg.securityUsername();
    if (securityName == null || securityName.isBlank()) {
      throw new IOException("SNMP v3 security name is not configured");
    }
    OctetString securityNameOctet = new OctetString(securityName);
    byte[] authoritativeEngineId = Arrays.copyOf(engineId, engineId.length);
    OctetString engineIdOctet = new OctetString();
    engineIdOctet.setValue(authoritativeEngineId);
    String authPassword = cfg.authPassword();
    String privacyPassword = cfg.privacyPassword();
    snmp.getUSM().addUser(
        securityNameOctet,
        engineIdOctet,
        new UsmUser(
            securityNameOctet,
            resolveAuthProtocol(cfg.authProtocol()),
            authPassword == null || authPassword.isBlank() ? null : new OctetString(authPassword),
            resolvePrivProtocol(cfg.privacyProtocol()),
            privacyPassword == null || privacyPassword.isBlank() ? null : new OctetString(privacyPassword)
        )
    );
    UserTarget<Address> target = new UserTarget<>();
    target.setAddress(targetAddress);
    target.setRetries(retries);
    target.setTimeout(timeout);
    target.setVersion(SnmpConstants.version3);
    target.setSecurityLevel(resolveSecurityLevel(cfg));
    target.setSecurityName(securityNameOctet);
    target.setAuthoritativeEngineID(authoritativeEngineId);
    return target;
  }

  /**
   * Engine ID discovery is expensive and flaky under parallel collector load; cache per host/port/user.
   */
  private byte[] resolveAuthoritativeEngineId(
      Snmp snmp,
      Address targetAddress,
      MonitoringTemplateSnmp cfg,
      int timeout
  ) throws IOException {
    String securityName = cfg.securityUsername() == null ? "" : cfg.securityUsername().trim();
    String cacheKey = targetAddress + "|" + resolveSnmpPort(cfg) + "|" + securityName;
    long now = System.currentTimeMillis();
    CachedV3Engine hit = V3_ENGINE_CACHE.get(cacheKey);
    if (hit != null && hit.expiresAtEpochMs() > now) {
      return Arrays.copyOf(hit.engineId(), hit.engineId().length);
    }
    synchronized (("snmpv3-engine:" + cacheKey).intern()) {
      hit = V3_ENGINE_CACHE.get(cacheKey);
      if (hit != null && hit.expiresAtEpochMs() > now) {
        return Arrays.copyOf(hit.engineId(), hit.engineId().length);
      }
      byte[] engineId = snmp.discoverAuthoritativeEngineID(targetAddress, timeout);
      if (engineId == null || engineId.length == 0) {
        throw new IOException("SNMP v3 engine ID discovery failed for " + targetAddress);
      }
      byte[] copy = Arrays.copyOf(engineId, engineId.length);
      V3_ENGINE_CACHE.put(cacheKey, new CachedV3Engine(copy, now + V3_ENGINE_CACHE_TTL_MS));
      return Arrays.copyOf(copy, copy.length);
    }
  }

  private int resolveSecurityLevel(MonitoringTemplateSnmp cfg) {
    boolean hasAuth = cfg.authPassword() != null && !cfg.authPassword().isBlank();
    boolean hasPriv = cfg.privacyPassword() != null && !cfg.privacyPassword().isBlank();
    if (hasAuth && hasPriv) {
      return SecurityLevel.AUTH_PRIV;
    }
    if (hasAuth) {
      return SecurityLevel.AUTH_NOPRIV;
    }
    return SecurityLevel.NOAUTH_NOPRIV;
  }

  private PDU newPduForTarget(Target<Address> target) {
    return target.getVersion() == SnmpConstants.version3 ? new ScopedPDU() : new PDU();
  }

  private int resolveSnmpPort(MonitoringTemplateSnmp snmp) {
    if (snmp != null && snmp.port() != null && snmp.port() > 0) {
      return snmp.port();
    }
    return 161;
  }

  private Map<String, String> readOidValues(String ip, ResolvedMonitoringTemplate template, Map<String, String> requestedOids) {
    Map<String, String> getRequests = new LinkedHashMap<>();
    Map<String, List<String>> walkRequests = new LinkedHashMap<>();
    for (Map.Entry<String, String> requested : requestedOids.entrySet()) {
      String resolved = resolveOidExpression(requested.getValue());
      if (resolved == null || resolved.isBlank()) {
        continue;
      }
      List<String> walkColumns = parseWalkColumns(resolved);
      if (!walkColumns.isEmpty()) {
        walkRequests.put(requested.getKey(), walkColumns);
        continue;
      }
      String getOid = parseGetOid(resolved);
      if (isDiscreteSnmpOid(getOid)) {
        getRequests.put(requested.getKey(), getOid);
      }
    }

    Map<String, String> resolvedValues = new LinkedHashMap<>();
    if (!getRequests.isEmpty()) {
      try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
           Snmp snmp = new Snmp(transport)) {
        transport.listen();
        Target<Address> target = buildSnmpTarget(snmp, ip, template.snmp());
        List<Map.Entry<String, String>> entries = new ArrayList<>(getRequests.entrySet());
        for (int start = 0; start < entries.size(); start += MAX_SNMP_GET_BATCH_SIZE) {
          int end = Math.min(start + MAX_SNMP_GET_BATCH_SIZE, entries.size());
          Map<String, String> batch = new LinkedHashMap<>();
          for (int i = start; i < end; i++) {
            batch.put(entries.get(i).getKey(), entries.get(i).getValue());
          }
          resolvedValues.putAll(readOidValuesBatch(snmp, target, batch));
        }
      } catch (IOException exception) {
        return Map.of();
      }
    }

    if (!walkRequests.isEmpty()) {
      try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
           Snmp snmp = new Snmp(transport)) {
        transport.listen();
        Target<Address> target = buildSnmpTarget(snmp, ip, template.snmp());
        for (Map.Entry<String, List<String>> request : walkRequests.entrySet()) {
          String payload = readWalkExpressionPayload(snmp, target, request.getValue());
          if (payload != null) {
            resolvedValues.put(request.getKey(), payload);
          }
        }
      } catch (IOException exception) {
        log.warn("SNMP walk batch failed for {} (version={}): {}", ip,
            template.snmp() == null ? "?" : template.snmp().version(), exception.getMessage());
      }
    }
    return resolvedValues;
  }

  private Map<String, String> readOidValuesBatch(
      Snmp snmp,
      Target<Address> target,
      Map<String, String> requestedOids
  ) throws IOException {
    PDU pdu = newPduForTarget(target);
    for (String oidValue : requestedOids.values()) {
      try {
        pdu.add(new VariableBinding(new OID(oidValue)));
      } catch (RuntimeException exception) {
        log.debug("Skip invalid SNMP OID in GET batch: {}", oidValue);
      }
    }
    if (pdu.getVariableBindings().isEmpty()) {
      return Map.of();
    }

    pdu.setType(PDU.GET);
    ResponseEvent<Address> event = snmp.send(pdu, target);
    if (event == null || event.getResponse() == null) {
      return Map.of();
    }

    Map<String, String> rawValues = new LinkedHashMap<>();
    for (VariableBinding binding : event.getResponse().getVariableBindings()) {
      if (binding.getVariable() == null || binding.getVariable().isException()) {
        continue;
      }
      rawValues.put(binding.getOid().toDottedString(), binding.getVariable().toString());
    }

    Map<String, String> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, String> requested : requestedOids.entrySet()) {
      resolved.put(requested.getKey(), rawValues.get(requested.getValue()));
    }
    return resolved;
  }

  private Map<String, String> walkOidValues(String ip, ResolvedMonitoringTemplate template, OID baseOid) {
    try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
         Snmp snmp = new Snmp(transport)) {
      transport.listen();
      Target<Address> target = buildSnmpTarget(snmp, ip, template.snmp());
      return walkOidValues(snmp, target, baseOid);
    } catch (IOException exception) {
      return Map.of();
    }
  }

  private Map<String, String> walkOidValues(Snmp snmp, Target<Address> target, OID baseOid) {
    Map<String, String> results = new LinkedHashMap<>();
    try {
      walkColumnValues(snmp, target, baseOid, results::put);
    } catch (IOException exception) {
      return Map.of();
    }
    return results;
  }

  private String readWalkExpressionPayload(String ip, ResolvedMonitoringTemplate template, List<String> columnOids) {
    try (TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
         Snmp snmp = new Snmp(transport)) {
      transport.listen();
      Target<Address> target = buildSnmpTarget(snmp, ip, template.snmp());
      return readWalkExpressionPayload(snmp, target, columnOids);
    } catch (IOException exception) {
      return "[]";
    }
  }

  private String readWalkExpressionPayload(
      Snmp snmp,
      Target<Address> target,
      List<String> columnOids
  ) {
    if (columnOids == null || columnOids.isEmpty()) {
      return "[]";
    }
    Map<String, Map<String, String>> columnsBySuffix = new LinkedHashMap<>();
    Set<String> suffixes = new java.util.LinkedHashSet<>();
    Map<String, String> scalarColumnFields = new LinkedHashMap<>();
    for (int i = 0; i < columnOids.size(); i++) {
      String columnOid = columnOids.get(i);
      if (!isDiscreteSnmpOid(columnOid)) {
        continue;
      }
      String fieldName = aliasForWalkOid(columnOid, i);
      Map<String, String> values = walkOidValues(snmp, target, new OID(columnOid));
      if (values.isEmpty()) {
        /*
         * SNMP walk по скалярному OID (.0) не даёт суффикса строки (GETNEXT возвращает тот же OID) —
         * тогда подставляем значение через GET и дублируем его во все строки таблицы.
         * Для колонок-таблиц без .0 пустой walk означает реальную ошибку/отсутствие данных: не делаем GET
         * по «базе» колонки (некорректный запрос, таймауты, риск сорвать весь цикл опроса).
         */
        if (columnOid.trim().endsWith(".0")) {
          try {
            String scalar = snmpGetScalar(snmp, target, columnOid);
            if (scalar != null && !scalar.isBlank()) {
              scalarColumnFields.put(fieldName, scalar);
            }
          } catch (IOException exception) {
            log.debug("SNMP GET scalar for walk column {} failed: {}", columnOid, exception.getMessage());
          }
        }
        continue;
      }
      for (Map.Entry<String, String> value : values.entrySet()) {
        suffixes.add(value.getKey());
        columnsBySuffix
            .computeIfAbsent(value.getKey(), ignored -> new LinkedHashMap<>())
            .put(fieldName, value.getValue());
      }
    }
    if (!scalarColumnFields.isEmpty() && !suffixes.isEmpty()) {
      for (String suffix : suffixes) {
        Map<String, String> row = columnsBySuffix.computeIfAbsent(suffix, ignored -> new LinkedHashMap<>());
        for (Map.Entry<String, String> entry : scalarColumnFields.entrySet()) {
          row.putIfAbsent(entry.getKey(), entry.getValue());
        }
      }
    }
    if (suffixes.isEmpty()) {
      return "[]";
    }

    List<Map<String, String>> rows = suffixes.stream()
        .sorted(this::compareSuffixes)
        .map(suffix -> {
          Map<String, String> row = new LinkedHashMap<>();
          row.put("index", suffix);
          Map<String, String> values = columnsBySuffix.get(suffix);
          if (values != null) {
            row.putAll(values);
          }
          return row;
        })
        .toList();
    try {
      return OBJECT_MAPPER.writeValueAsString(rows);
    } catch (JsonProcessingException exception) {
      return "[]";
    }
  }

  private String snmpGetScalar(Snmp snmp, Target<Address> target, String oid) throws IOException {
    PDU pdu = newPduForTarget(target);
    pdu.add(new VariableBinding(new OID(oid)));
    pdu.setType(PDU.GET);
    ResponseEvent<Address> event = snmp.send(pdu, target);
    if (event == null || event.getResponse() == null || event.getResponse().getVariableBindings().isEmpty()) {
      return null;
    }
    VariableBinding binding = event.getResponse().get(0);
    if (binding.getVariable() == null || binding.getVariable().isException()) {
      return null;
    }
    return binding.getVariable().toString();
  }

  private List<String> parseWalkColumns(String expression) {
    if (expression == null) {
      return List.of();
    }
    String trimmed = expression.trim();
    if (!trimmed.startsWith("walk[") || !trimmed.endsWith("]")) {
      return List.of();
    }
    String body = trimmed.substring("walk[".length(), trimmed.length() - 1).trim();
    if (body.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(body.split(","))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .map(this::resolveOidExpression)
        .toList();
  }

  private String parseGetOid(String expression) {
    if (expression == null) {
      return null;
    }
    String trimmed = expression.trim();
    if (!trimmed.startsWith("get[") || !trimmed.endsWith("]")) {
      return trimmed;
    }
    String body = trimmed.substring("get[".length(), trimmed.length() - 1).trim();
    if (body.isBlank()) {
      return null;
    }
    int commaIdx = body.indexOf(',');
    String firstToken = commaIdx < 0 ? body : body.substring(0, commaIdx);
    return firstToken.trim();
  }

  private String aliasForWalkOid(String oid, int index) {
    return WALK_OID_FIELD_ALIASES.getOrDefault(oid, "col" + (index + 1));
  }

  private int compareSuffixes(String left, String right) {
    try {
      return Comparator.comparingLong((String value) -> Long.parseLong(value.replace(".", "")))
          .compare(left, right);
    } catch (RuntimeException exception) {
      return left.compareTo(right);
    }
  }

  private ResolvedMonitoringTemplate legacyMonitoringTemplate(int port, int timeout, int retries, String community) {
    return new ResolvedMonitoringTemplate(
        "legacy",
        "SNMP",
        "legacy",
        "",
        null,
        null,
        null,
        0,
        "1",
        "legacy",
        "1.0.0",
        MonitoringTemplateSnmp.v2c(firstNonBlank(community, "public"), timeout, retries, port),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
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

  private MonitoringDetailsDto emptyMonitoringDetails() {
    return new MonitoringDetailsDto(
        new MonitoringMetricDto(null, null, null, null, null, null),
        null,
        null,
        "-",
        "-",
        "-",
        "-",
        "-",
        "-",
        "-",
        null,
        "DIRECT_SNMP",
        false
    );
  }

  private int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private long parseLong(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      java.util.regex.Matcher ticksInParentheses = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(value);
      if (ticksInParentheses.find()) {
        try {
          return Long.parseLong(ticksInParentheses.group(1));
        } catch (NumberFormatException ignored) {
          // Fallback below.
        }
      }
      java.util.regex.Matcher firstNumber = java.util.regex.Pattern.compile("(\\d+)").matcher(value);
      if (firstNumber.find()) {
        try {
          return Long.parseLong(firstNumber.group(1));
        } catch (NumberFormatException ignored) {
          return 0L;
        }
      }
      return 0L;
    }
  }

  private double parseDouble(String value, double fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private int computeUsagePercent(long used, long free) {
    long total = used + free;
    if (total <= 0) {
      return 0;
    }
    return clampPercent((int) Math.round((used * 100.0) / total));
  }

  private int clampPercent(int value) {
    return Math.max(0, Math.min(value, 100));
  }

  static TelemetrySnapshot resolveTelemetrySnapshot(
      Map<String, Double> values,
      Map<String, ZabbixItemRuntime> definitions
  ) {
    Map<String, Double> safeValues = values == null ? Map.of() : values;
    Map<String, ZabbixItemRuntime> safeDefinitions = definitions == null ? Map.of() : definitions;

    Double cpuCurrent = null;
    Double cpuAverage = null;
    Double cpuPeak = null;
    String nameCurrent = null;
    String nameAverage = null;
    String namePeak = null;

    ResolvedMetric resolved;
    resolved = firstResolvedForTemplateKeyBase(safeValues, safeDefinitions, "system.cpu.load.avg1");
    if (resolved != null) {
      cpuCurrent = resolved.value();
      nameCurrent = itemDisplayName(safeDefinitions, resolved.itemKey());
    }
    resolved = firstResolvedForTemplateKeyBase(safeValues, safeDefinitions, "system.cpu.load.avg5");
    if (resolved != null) {
      cpuAverage = resolved.value();
      nameAverage = itemDisplayName(safeDefinitions, resolved.itemKey());
    }
    resolved = firstResolvedForTemplateKeyBase(safeValues, safeDefinitions, "system.cpu.load.avg15");
    if (resolved != null) {
      cpuPeak = resolved.value();
      namePeak = itemDisplayName(safeDefinitions, resolved.itemKey());
    }

    if (cpuCurrent == null) {
      resolved = resolveFirstDeclaredAliasDouble(
          safeValues,
          safeDefinitions,
          "cpu_current",
          "rlCpuUtilDuringLast5Minutes"
      );
      if (resolved != null) {
        cpuCurrent = resolved.value();
        nameCurrent = itemDisplayName(safeDefinitions, resolved.itemKey());
      }
    }
    if (cpuAverage == null) {
      resolved = resolveFirstDeclaredAliasDouble(safeValues, safeDefinitions, "cpu_average");
      if (resolved != null) {
        cpuAverage = resolved.value();
        nameAverage = itemDisplayName(safeDefinitions, resolved.itemKey());
      }
    }
    if (cpuPeak == null) {
      resolved = resolveFirstDeclaredAliasDouble(safeValues, safeDefinitions, "cpu_peak");
      if (resolved != null) {
        cpuPeak = resolved.value();
        namePeak = itemDisplayName(safeDefinitions, resolved.itemKey());
      }
    }

    Integer ramUsedPercent = resolveMetricByAliases(safeValues, safeDefinitions, "ram_used_percent");
    Integer romUsedPercent = resolveMetricByAliases(safeValues, safeDefinitions, "rom_used_percent");

    List<MetricCandidate> candidates = buildMetricCandidates(safeValues, safeDefinitions);

    if (cpuCurrent == null) {
      MetricCandidate best = bestCandidate(candidates, SnmpScanServiceImpl::isCpuMetric, SnmpScanServiceImpl::scoreCpuCurrent);
      if (best != null) {
        cpuCurrent = best.value();
        nameCurrent = itemDisplayName(safeDefinitions, best.key());
      }
    }
    if (cpuAverage == null) {
      MetricCandidate best = bestCandidate(candidates, SnmpScanServiceImpl::isCpuMetric, SnmpScanServiceImpl::scoreCpuAverage);
      if (best != null) {
        cpuAverage = best.value();
        nameAverage = itemDisplayName(safeDefinitions, best.key());
      }
    }
    if (cpuPeak == null) {
      MetricCandidate best = bestCandidate(candidates, SnmpScanServiceImpl::isCpuMetric, SnmpScanServiceImpl::scoreCpuPeak);
      if (best != null) {
        cpuPeak = best.value();
        namePeak = itemDisplayName(safeDefinitions, best.key());
      }
    }

    // Keep the panel populated when template exposes only one CPU-like metric.
    if (cpuCurrent == null) {
      cpuCurrent = cpuAverage;
      nameCurrent = nameAverage;
    }
    if (cpuAverage == null) {
      cpuAverage = cpuCurrent;
      nameAverage = nameCurrent;
    }
    if (cpuPeak == null && cpuCurrent != null && cpuAverage != null) {
      cpuPeak = Math.max(cpuCurrent, cpuAverage);
      if (Double.compare(cpuPeak, cpuCurrent) == 0) {
        namePeak = nameCurrent;
      } else {
        namePeak = nameAverage;
      }
    }

    if (ramUsedPercent == null) {
      ramUsedPercent = resolveUsedPercent(candidates, MetricDomain.RAM);
    }
    if (romUsedPercent == null) {
      romUsedPercent = resolveUsedPercent(candidates, MetricDomain.ROM);
    }

    return new TelemetrySnapshot(
        cpuCurrent,
        cpuAverage,
        cpuPeak,
        nameCurrent,
        nameAverage,
        namePeak,
        ramUsedPercent,
        romUsedPercent
    );
  }

  private static List<MetricCandidate> buildMetricCandidates(
      Map<String, Double> values,
      Map<String, ZabbixItemRuntime> definitions
  ) {
    List<MetricCandidate> candidates = new ArrayList<>();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
        continue;
      }
      String key = entry.getKey();
      ZabbixItemRuntime definition = definitions.get(key);
      String name = definition == null || definition.name() == null || definition.name().isBlank()
          ? key
          : definition.name();
      String unit = definition == null || definition.units() == null ? "" : definition.units();
      candidates.add(new MetricCandidate(
          key,
          normalizeMetricText(key),
          normalizeMetricText(name),
          normalizeMetricText(unit),
          entry.getValue()
      ));
    }
    return List.copyOf(candidates);
  }

  private static Integer resolveUsedPercent(List<MetricCandidate> candidates, MetricDomain domain) {
    MetricCandidate percent = bestCandidate(
        candidates,
        c -> isMemoryMetric(c, domain) && isPercentLike(c),
        c -> 500 + domainScore(c, domain) + percentScore(c)
    );
    if (percent != null) {
      return clampPercentStatic((int) Math.round(percent.value()));
    }

    MetricCandidate used = bestCandidate(
        candidates,
        c -> isMemoryMetric(c, domain) && isUsedLike(c),
        c -> 300 + domainScore(c, domain) + ioRoleScore(c, "used")
    );
    MetricCandidate free = bestCandidate(
        candidates,
        c -> isMemoryMetric(c, domain) && isFreeLike(c),
        c -> 300 + domainScore(c, domain) + ioRoleScore(c, "free")
    );
    MetricCandidate total = bestCandidate(
        candidates,
        c -> isMemoryMetric(c, domain) && isTotalLike(c),
        c -> 300 + domainScore(c, domain) + ioRoleScore(c, "total")
    );

    if (used != null && total != null && total.value() > 0.0d) {
      return clampPercentStatic((int) Math.round((used.value() * 100.0d) / total.value()));
    }
    if (free != null && total != null && total.value() > 0.0d) {
      return clampPercentStatic((int) Math.round(((total.value() - free.value()) * 100.0d) / total.value()));
    }
    if (used != null && free != null && (used.value() + free.value()) > 0.0d) {
      return clampPercentStatic((int) Math.round((used.value() * 100.0d) / (used.value() + free.value())));
    }
    return null;
  }

  private static boolean isCpuMetric(MetricCandidate candidate) {
    return containsAnyToken(candidate, CPU_HINTS);
  }

  private static boolean isMemoryMetric(MetricCandidate candidate, MetricDomain domain) {
    boolean looksRom = containsAnyToken(candidate, ROM_HINTS);
    boolean looksRam = containsAnyToken(candidate, RAM_HINTS);
    return switch (domain) {
      case RAM -> looksRam && !looksRom;
      case ROM -> looksRom;
    };
  }

  private static int scoreCpuCurrent(MetricCandidate c) {
    int score = 0;
    if (containsToken(c, "cpu_current")) {
      score += 300;
    }
    if (containsToken(c, "avg1") || containsToken(c, "laload.1")) {
      score += 140;
    }
    if (containsToken(c, "usage") || containsToken(c, "util") || containsToken(c, "percent")) {
      score += 110;
    }
    if (containsToken(c, "avg5")) {
      score += 70;
    }
    if (containsToken(c, "avg15")) {
      score += 50;
    }
    if (containsToken(c, "peak") || containsToken(c, "max")) {
      score -= 80;
    }
    if (containsToken(c, "%") || containsToken(c, "percent")) {
      score += 20;
    }
    return score;
  }

  private static int scoreCpuAverage(MetricCandidate c) {
    int score = 0;
    if (containsToken(c, "cpu_average")) {
      score += 300;
    }
    if (containsToken(c, "avg5")) {
      score += 150;
    }
    if (containsToken(c, "avg15") || containsToken(c, "laload.3")) {
      score += 130;
    }
    if (containsToken(c, "avg1")) {
      score += 90;
    }
    if (containsToken(c, "usage") || containsToken(c, "util")) {
      score += 70;
    }
    return score;
  }

  private static int scoreCpuPeak(MetricCandidate c) {
    int score = 0;
    if (containsToken(c, "cpu_peak")) {
      score += 300;
    }
    if (containsToken(c, "peak") || containsToken(c, "max")) {
      score += 170;
    }
    if (containsToken(c, "avg15") || containsToken(c, "laload.3")) {
      score += 80;
    }
    if (containsToken(c, "avg5")) {
      score += 50;
    }
    if (containsToken(c, "avg1")) {
      score += 30;
    }
    return score;
  }

  private static int domainScore(MetricCandidate candidate, MetricDomain domain) {
    int score = containsAnyToken(candidate, RAM_HINTS) ? 25 : 0;
    if (containsAnyToken(candidate, ROM_HINTS)) {
      score += domain == MetricDomain.ROM ? 60 : -40;
    }
    return score;
  }

  private static int percentScore(MetricCandidate candidate) {
    int score = 0;
    if (containsToken(candidate, "percent") || containsToken(candidate, "pct")) {
      score += 70;
    }
    if ("%".equals(candidate.normalizedUnit()) || containsToken(candidate, "%")) {
      score += 60;
    }
    if (containsToken(candidate, "usage") || containsToken(candidate, "util")) {
      score += 30;
    }
    return score;
  }

  private static int ioRoleScore(MetricCandidate candidate, String role) {
    int score = 0;
    if (containsToken(candidate, role)) {
      score += 80;
    }
    if ("free".equals(role) && containsToken(candidate, "avail")) {
      score += 50;
    }
    if ("total".equals(role) && (containsToken(candidate, "size") || containsToken(candidate, "capacity"))) {
      score += 50;
    }
    return score;
  }

  private static boolean isPercentLike(MetricCandidate c) {
    return percentScore(c) > 0;
  }

  private static boolean isUsedLike(MetricCandidate c) {
    return containsToken(c, "used");
  }

  private static boolean isFreeLike(MetricCandidate c) {
    return containsToken(c, "free") || containsToken(c, "avail");
  }

  private static boolean isTotalLike(MetricCandidate c) {
    return containsToken(c, "total") || containsToken(c, "size")
        || containsToken(c, "capacity") || containsToken(c, "max");
  }

  private static boolean containsAnyToken(MetricCandidate c, List<String> tokens) {
    for (String token : tokens) {
      if (containsToken(c, token)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsToken(MetricCandidate c, String token) {
    String normalizedToken = normalizeMetricText(token);
    if (normalizedToken.isBlank()) {
      return false;
    }
    String key = c.normalizedKey();
    String name = c.normalizedName();
    if (key.contains(normalizedToken) || name.contains(normalizedToken)) {
      return true;
    }
    String compactToken = normalizedToken.replace(".", "");
    if (compactToken.isBlank()) {
      return false;
    }
    return key.replace(".", "").contains(compactToken)
        || name.replace(".", "").contains(compactToken);
  }

  private static String normalizeMetricText(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    return raw.toLowerCase()
        .replaceAll("\\[[^\\]]*\\]", "")
        .replaceAll("[^a-z0-9%]+", ".")
        .replaceAll("\\.{2,}", ".")
        .replaceAll("^\\.|\\.$", "");
  }

  private static Integer roundToInt(Double value) {
    if (value == null || !Double.isFinite(value)) {
      return null;
    }
    return (int) Math.round(value);
  }

  private static <T> T bestCandidate(
      List<T> candidates,
      java.util.function.Predicate<T> filter,
      java.util.function.ToIntFunction<T> score
  ) {
    T best = null;
    int bestScore = Integer.MIN_VALUE;
    for (T candidate : candidates) {
      if (!filter.test(candidate)) {
        continue;
      }
      int candidateScore = score.applyAsInt(candidate);
      if (candidateScore > bestScore) {
        best = candidate;
        bestScore = candidateScore;
      }
    }
    return best;
  }

  private static int clampPercentStatic(int value) {
    return Math.max(0, Math.min(value, 100));
  }

  private static String itemDisplayName(Map<String, ZabbixItemRuntime> definitions, String itemKey) {
    if (itemKey == null || itemKey.isBlank()) {
      return null;
    }
    ZabbixItemRuntime rt = definitions.get(itemKey);
    if (rt != null && rt.name() != null && !rt.name().isBlank()) {
      return rt.name().trim();
    }
    return itemKey;
  }

  /**
   * Первая метрика в шаблоне с ключом {@code base} или {@code base[...]} (как в Zabbix).
   */
  private static ResolvedMetric firstResolvedForTemplateKeyBase(
      Map<String, Double> values,
      Map<String, ZabbixItemRuntime> definitions,
      String base
  ) {
    if (base == null || base.isBlank() || definitions.isEmpty()) {
      return null;
    }
    String baseNorm = base.toLowerCase(Locale.ROOT);
    for (String key : definitions.keySet()) {
      if (key == null || key.isBlank()) {
        continue;
      }
      String k = key.toLowerCase(Locale.ROOT);
      if (k.equals(baseNorm) || k.startsWith(baseNorm + "[")) {
        Double v = values.get(key);
        if (v != null && Double.isFinite(v)) {
          return new ResolvedMetric(key, v);
        }
      }
    }
    return null;
  }

  /**
   * Как {@link #resolveMetricByAliases}, но возвращает сырое double и ключ item из шаблона (для подписи).
   */
  private static ResolvedMetric resolveFirstDeclaredAliasDouble(
      Map<String, Double> values,
      Map<String, ZabbixItemRuntime> definitions,
      String... names
  ) {
    if (names == null || names.length == 0) {
      return null;
    }
    boolean declaredInTemplate = false;
    for (String name : names) {
      if (definitions.containsKey(name)) {
        declaredInTemplate = true;
        break;
      }
    }
    if (!declaredInTemplate) {
      return null;
    }
    for (String name : names) {
      Double value = values.get(name);
      if (value != null && Double.isFinite(value)) {
        return new ResolvedMetric(name, value);
      }
    }
    return null;
  }

  private static Integer resolveMetricByAliases(
      Map<String, Double> values,
      Map<String, ZabbixItemRuntime> definitions,
      String... names
  ) {
    if (names == null || names.length == 0) {
      return null;
    }
    boolean declaredInTemplate = false;
    for (String name : names) {
      if (definitions.containsKey(name)) {
        declaredInTemplate = true;
        break;
      }
    }
    if (!declaredInTemplate) {
      return null;
    }
    for (String name : names) {
      Double value = values.get(name);
      if (value != null) {
        return (int) Math.round(value);
      }
    }
    return null;
  }

  enum MetricDomain {
    RAM, ROM
  }

  record MetricCandidate(
      String key,
      String normalizedKey,
      String normalizedName,
      String normalizedUnit,
      double value
  ) {
  }

  private record ResolvedMetric(String itemKey, Double value) {
  }

  record TelemetrySnapshot(
      Double cpuCurrent,
      Double cpuAverage,
      Double cpuPeak,
      String cpuCurrentItemName,
      String cpuAverageItemName,
      String cpuPeakItemName,
      Integer ramUsedPercent,
      Integer romUsedPercent
  ) {
  }

  private String formatUptime(String value) {
    long ticks = parseUptimeTicks(value);
    if (ticks <= 0) {
      return "-";
    }
    long totalSeconds = ticks / 100;
    long days = totalSeconds / 86_400;
    long hours = (totalSeconds % 86_400) / 3_600;
    long minutes = (totalSeconds % 3_600) / 60;
    long seconds = totalSeconds % 60;
    return days + " дней, " + String.format("%02d:%02d:%02d", hours, minutes, seconds);
  }

  private long parseUptimeTicks(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }

    java.util.regex.Matcher ticksInParentheses = java.util.regex.Pattern.compile("\\((\\d+)\\)").matcher(value);
    if (ticksInParentheses.find()) {
      return parseLong(ticksInParentheses.group(1));
    }

    java.util.regex.Matcher dhmsMatcher = java.util.regex.Pattern.compile(
        "(?i)(\\d+)\\s+days?\\s+(\\d+)\\s+hours?\\s+(\\d+)\\s+minutes?\\s+(\\d+)\\s+seconds?"
    ).matcher(value);
    if (dhmsMatcher.find()) {
      long days = parseLong(dhmsMatcher.group(1));
      long hours = parseLong(dhmsMatcher.group(2));
      long minutes = parseLong(dhmsMatcher.group(3));
      long seconds = parseLong(dhmsMatcher.group(4));
      long totalSeconds = days * 86_400 + hours * 3_600 + minutes * 60 + seconds;
      return totalSeconds * 100;
    }

    java.util.regex.Matcher hmsMatcher = java.util.regex.Pattern.compile(
        "(?i)(\\d+)\\s+hours?\\s+(\\d+)\\s+minutes?\\s+(\\d+)\\s+seconds?"
    ).matcher(value);
    if (hmsMatcher.find()) {
      long hours = parseLong(hmsMatcher.group(1));
      long minutes = parseLong(hmsMatcher.group(2));
      long seconds = parseLong(hmsMatcher.group(3));
      long totalSeconds = hours * 3_600 + minutes * 60 + seconds;
      return totalSeconds * 100;
    }

    java.util.regex.Matcher dayClockMatcher = java.util.regex.Pattern.compile(
        "(?i)(\\d+)\\s+days?,\\s*(\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?"
    ).matcher(value);
    if (dayClockMatcher.find()) {
      long days = parseLong(dayClockMatcher.group(1));
      long hours = parseLong(dayClockMatcher.group(2));
      long minutes = parseLong(dayClockMatcher.group(3));
      long seconds = parseLong(dayClockMatcher.group(4));
      long hundredths = parseLong(dayClockMatcher.group(5));
      long totalSeconds = days * 86_400 + hours * 3_600 + minutes * 60 + seconds;
      return totalSeconds * 100 + Math.min(hundredths, 99);
    }

    return parseLong(value);
  }

  private String normalizeOid(String oid) {
    if (oid == null || oid.isBlank() || "-".equals(oid)) {
      return null;
    }
    return oid.startsWith(".") ? oid : "." + oid;
  }

  private static Map<String, String> createEnterpriseVendors() {
    Map<String, String> vendors = new LinkedHashMap<>();
    vendors.put(".1.3.6.1.4.1.9", "Cisco");
    vendors.put(".1.3.6.1.4.1.11", "HP");
    vendors.put(".1.3.6.1.4.1.43", "3Com");
    vendors.put(".1.3.6.1.4.1.171", "D-Link");
    vendors.put(".1.3.6.1.4.1.2011", "Huawei");
    vendors.put(".1.3.6.1.4.1.25506", "H3C");
    vendors.put(".1.3.6.1.4.1.2636", "Juniper");
    vendors.put(".1.3.6.1.4.1.14988", "MikroTik");
    vendors.put(".1.3.6.1.4.1.1991", "Foundry");
    vendors.put(".1.3.6.1.4.1.8072", "Linux (net-snmp)");
    return vendors;
  }

  private String normalizeScanMode(String scanMode) {
    return scanMode == null ? "SNMP_V2" : scanMode.trim().toUpperCase();
  }

  private boolean isSnmpMode(String scanMode) {
    return scanMode.startsWith("SNMP");
  }

  private boolean isHttpMode(String scanMode) {
    return "HTTP".equals(scanMode) || "HTTPS".equals(scanMode);
  }

  private boolean isPortBasedMode(String scanMode) {
    return List.of("TCP", "FTP", "SSH", "TELNET", "SMTP", "POP", "IMAP", "LDAP", "NNTP")
        .contains(scanMode);
  }

  private boolean usesProbePort(String scanMode) {
    return !"ICMP".equals(scanMode) && !"DNS".equals(scanMode);
  }

  private boolean isPortOpen(String ip, int port, int timeout) {
    try (Socket socket = new Socket()) {
      socket.connect(new java.net.InetSocketAddress(ip, port), timeout);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private boolean isHttpReachable(
      String ip,
      String scanMode,
      int port,
      int timeout,
      DiscoveryProbeConfig probe
  ) {
    try {
      URL url = URI.create(scanMode.toLowerCase() + "://" + ip + ":" + port + "/").toURL();
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setConnectTimeout(timeout);
      connection.setReadTimeout(timeout);
      connection.setRequestMethod("GET");
      if (hasText(probe.username()) || hasText(probe.password())) {
        String credentials = probe.username() + ":" + (probe.password() == null ? "" : probe.password());
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encoded);
      }
      if ("HTTPS".equals(scanMode) && connection instanceof HttpsURLConnection httpsConnection) {
        applyHttpsClientConfig(httpsConnection, probe);
      }
      int code = connection.getResponseCode();
      return code > 0 && code < 500;
    } catch (IOException e) {
      return false;
    }
  }

  private void applyHttpsClientConfig(HttpsURLConnection connection, DiscoveryProbeConfig probe) {
    try {
      boolean insecure = Boolean.TRUE.equals(probe.insecureSkipVerify());
      boolean hasClientCert = hasText(probe.clientCertPem()) && hasText(probe.clientKeyPem());
      if (!insecure && !hasClientCert) {
        return;
      }
      SSLContext sslContext = SSLContext.getInstance("TLS");
      KeyManagerFactory keyManagerFactory = null;
      if (hasClientCert) {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        byte[] p12 = buildPkcs12FromPem(probe.clientCertPem(), probe.clientKeyPem());
        keyStore.load(new ByteArrayInputStream(p12), new char[0]);
        keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
      }
      TrustManager[] trustManagers = insecure
          ? new TrustManager[] {
              new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                  return new X509Certificate[0];
                }
              }
          }
          : null;
      sslContext.init(
          keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers(),
          trustManagers,
          new SecureRandom()
      );
      connection.setSSLSocketFactory(sslContext.getSocketFactory());
      if (insecure) {
        connection.setHostnameVerifier((hostname, session) -> true);
      }
    } catch (Exception e) {
      log.debug("Не удалось настроить HTTPS-клиент для probe: {}", e.getMessage());
    }
  }

  private byte[] buildPkcs12FromPem(String certPem, String keyPem) throws Exception {
    java.security.cert.CertificateFactory certificateFactory =
        java.security.cert.CertificateFactory.getInstance("X.509");
    java.security.cert.Certificate certificate = certificateFactory.generateCertificate(
        new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8))
    );
    byte[] keyBytes = parsePemPrivateKey(keyPem);
    java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
    java.security.PrivateKey privateKey =
        java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setKeyEntry("client", privateKey, new char[0], new java.security.cert.Certificate[] {certificate});
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    keyStore.store(output, new char[0]);
    return output.toByteArray();
  }

  private byte[] parsePemPrivateKey(String pem) {
    String normalized = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
        .replace("-----END RSA PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    return Base64.getDecoder().decode(normalized);
  }

  private boolean hasSshCredentials(DiscoveryProbeConfig probe) {
    return hasText(probe.username())
        && (hasText(probe.password()) || hasText(probe.privateKeyPem()));
  }

  private boolean isSshReachable(String ip, int port, DiscoveryProbeConfig probe, int timeout) {
    Session session = null;
    try {
      JSch jsch = new JSch();
      if (hasText(probe.privateKeyPem())) {
        byte[] keyBytes = probe.privateKeyPem().getBytes(StandardCharsets.UTF_8);
        if (hasText(probe.passphrase())) {
          jsch.addIdentity("probe-key", keyBytes, null, probe.passphrase().getBytes(StandardCharsets.UTF_8));
        } else {
          jsch.addIdentity("probe-key", keyBytes, null, null);
        }
      }
      session = jsch.getSession(probe.username(), ip, port);
      if (hasText(probe.password())) {
        session.setPassword(probe.password());
      }
      java.util.Properties config = new java.util.Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);
      session.setTimeout(timeout);
      session.connect(timeout);
      return session.isConnected();
    } catch (JSchException e) {
      return false;
    } finally {
      if (session != null && session.isConnected()) {
        session.disconnect();
      }
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String displayPollingStatus(String scanMode) {
    return switch (scanMode) {
      case "SNMP_V1" -> "SNMP v1";
      case "SNMP_V2" -> "SNMP v2c";
      case "SNMP_V3" -> "SNMP v3";
      case "DNS" -> "DNS (PTR)";
      default -> scanMode;
    };
  }

  private void walkColumn(
      Snmp snmp,
      Target<Address> target,
      OID baseOid,
      InterfaceValueConsumer consumer
  ) throws IOException {
    walkColumnValues(snmp, target, baseOid, (suffix, value) -> {
      int index = parseLastSubIdentifier(suffix);
      if (index >= 0) {
        consumer.accept(index, value);
      }
    });
  }

  private void walkColumnValues(
      Snmp snmp,
      Target<Address> target,
      OID baseOid,
      WalkValueConsumer consumer
  ) throws IOException {
    OID nextOid = baseOid;
    while (true) {
      PDU pdu = target.getVersion() == SnmpConstants.version3 ? new ScopedPDU() : new PDU();
      pdu.add(new VariableBinding(nextOid));
      pdu.setType(PDU.GETNEXT);

      ResponseEvent<Address> event = snmp.send(pdu, target);
      if (event == null || event.getResponse() == null || event.getResponse().getVariableBindings().isEmpty()) {
        break;
      }

      VariableBinding binding = event.getResponse().get(0);
      OID currentOid = binding.getOid();
      if (currentOid == null || !currentOid.startsWith(baseOid)) {
        break;
      }

      String suffix = oidSuffix(baseOid, currentOid);
      if (suffix == null || suffix.isBlank()) {
        break;
      }
      String value = binding.getVariable() == null ? "" : binding.getVariable().toString();
      consumer.accept(suffix, value);
      nextOid = currentOid;
    }
  }

  private InterfaceAccumulator accumulator(Map<Integer, InterfaceAccumulator> map, int index) {
    return map.computeIfAbsent(index, key -> new InterfaceAccumulator());
  }

  static List<DiscoveryColumnSpec> parseDiscoveryColumns(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    String trimmed = value.trim();
    if (!trimmed.startsWith("discovery[") || !trimmed.endsWith("]")) {
      return List.of();
    }
    String content = trimmed.substring("discovery[".length(), trimmed.length() - 1).trim();
    if (content.isBlank()) {
      return List.of();
    }
    String[] tokens = content.split(",");
    if (tokens.length < 2 || tokens.length % 2 != 0) {
      return List.of();
    }
    List<DiscoveryColumnSpec> columns = new ArrayList<>();
    for (int i = 0; i < tokens.length; i += 2) {
      String macro = tokens[i].trim();
      String oid = tokens[i + 1].trim();
      if (!macro.startsWith("{#") || !macro.endsWith("}") || oid.isBlank()) {
        return List.of();
      }
      columns.add(new DiscoveryColumnSpec(macro, oid));
    }
    return List.copyOf(columns);
  }

  /**
   * True if the string is a single numeric SNMP object identifier (suitable for GET or one SNMP walk column),
   * not a Zabbix discovery expression fragment.
   */
  static boolean isDiscreteSnmpOid(String oid) {
    if (oid == null || oid.isBlank()) {
      return false;
    }
    String t = oid.trim();
    if (t.contains("{#") || t.contains(",") || t.contains("discovery[")) {
      return false;
    }
    try {
      new OID(t);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  static boolean matchesDiscoveryFilter(ZabbixDiscoveryFilterRecord filter, Map<String, String> macros) {
    if (filter == null || filter.conditions() == null || filter.conditions().isEmpty()) {
      return true;
    }
    boolean orMode = "OR".equalsIgnoreCase(filter.evaltype());
    for (ZabbixDiscoveryConditionRecord condition : filter.conditions()) {
      boolean matches = matchesDiscoveryCondition(condition, macros);
      if (orMode && matches) {
        return true;
      }
      if (!orMode && !matches) {
        return false;
      }
    }
    return !orMode;
  }

  private static boolean matchesDiscoveryCondition(
      ZabbixDiscoveryConditionRecord condition,
      Map<String, String> macros
  ) {
    if (condition == null || condition.macro() == null || condition.macro().isBlank()) {
      return true;
    }
    String actual = macros.getOrDefault(condition.macro(), "");
    String expected = condition.value() == null ? "" : condition.value();
    String operator = condition.operator() == null ? "MATCHES_REGEX" : condition.operator().trim().toUpperCase();
    return switch (operator) {
      case "NOT_MATCHES_REGEX", "NOT_MATCHES" -> !safeRegexMatches(actual, expected);
      case "EQUALS" -> actual.equals(expected);
      case "NOT_EQUALS" -> !actual.equals(expected);
      case "EXISTS" -> macros.containsKey(condition.macro()) && !actual.isBlank();
      case "NOT_EXISTS" -> !macros.containsKey(condition.macro()) || actual.isBlank();
      case "MATCHES_REGEX", "MATCHES", "" -> safeRegexMatches(actual, expected);
      default -> safeRegexMatches(actual, expected);
    };
  }

  private static boolean safeRegexMatches(String actual, String expected) {
    if (expected == null || expected.isBlank()) {
      return true;
    }
    try {
      return actual.matches(expected);
    } catch (java.util.regex.PatternSyntaxException exception) {
      // Some exported filters use unresolved Zabbix user macros like {$NET.IF.IFNAME.MATCHES}.
      // Skip such conditions instead of aborting the whole metric collection cycle.
      return true;
    }
  }

  private List<DiscoveryColumnSpec> resolveDiscoveryColumns(List<DiscoveryColumnSpec> columns) {
    if (columns == null || columns.isEmpty()) {
      return List.of();
    }
    List<DiscoveryColumnSpec> resolved = new ArrayList<>();
    for (DiscoveryColumnSpec column : columns) {
      String oid = resolveOidExpression(column.oid());
      if (!isDiscreteSnmpOid(oid)) {
        log.debug("Skip unsupported discovery column {} -> {}", column.macro(), column.oid());
        return List.of();
      }
      resolved.add(new DiscoveryColumnSpec(column.macro(), oid));
    }
    return List.copyOf(resolved);
  }

  private String discoveryColumnValueKey(String suffix, String macro) {
    return suffix + "|" + macro;
  }

  private String oidSuffix(OID baseOid, OID currentOid) {
    if (baseOid == null || currentOid == null || currentOid.size() <= baseOid.size()) {
      return null;
    }
    StringBuilder suffix = new StringBuilder();
    for (int i = baseOid.size(); i < currentOid.size(); i++) {
      if (suffix.length() > 0) {
        suffix.append('.');
      }
      suffix.append(currentOid.get(i));
    }
    return suffix.toString();
  }

  private int parseLastSubIdentifier(String suffix) {
    if (suffix == null || suffix.isBlank()) {
      return -1;
    }
    int separator = suffix.lastIndexOf('.');
    String lastPart = separator >= 0 ? suffix.substring(separator + 1) : suffix;
    try {
      return Integer.parseInt(lastPart);
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  private String toStatus(String value) {
    try {
      return Integer.parseInt(value) == 1 ? "UP" : "DOWN";
    } catch (NumberFormatException exception) {
      return "DOWN";
    }
  }

  private String toSpeed(String value) {
    try {
      long bps = Long.parseLong(value);
      if (bps >= 1_000_000_000L) {
        return (bps / 1_000_000_000L) + " Gb/s";
      }
      if (bps >= 1_000_000L) {
        return (bps / 1_000_000L) + " Mb/s";
      }
      if (bps >= 1_000L) {
        return (bps / 1_000L) + " Kb/s";
      }
      return bps + " b/s";
    } catch (NumberFormatException exception) {
      return "-";
    }
  }

  private DeviceInterfaceDto toInterfaceDto(InterfaceAccumulator source) {
    String name = firstNonBlank(source.name, "-");
    String description = firstNonBlank(source.description, "-");
    String purpose = source.alias == null || source.alias.isBlank() ? description : source.alias;
    String operStatus = firstNonBlank(source.operStatus, "DOWN");
    return new DeviceInterfaceDto(
        name,
        description,
        firstNonBlank(source.adminStatus, "DOWN"),
        operStatus,
        "Нет",
        firstNonBlank(source.nominalSpeed, "-"),
        "UP".equals(operStatus) ? firstNonBlank(source.nominalSpeed, "-") : "0 b/s",
        purpose,
        isLogicalInterface(name) ? "L3" : "Access",
        isLogicalInterface(name) ? "logical" : "physical"
    );
  }

  private boolean isLogicalInterface(String name) {
    String normalized = name == null ? "" : name.toLowerCase();
    return normalized.startsWith("vlan")
        || normalized.startsWith("loopback")
        || normalized.startsWith("lo")
        || normalized.startsWith("port-channel")
        || normalized.startsWith("po");
  }

  @FunctionalInterface
  private interface InterfaceValueConsumer {
    void accept(int index, String value);
  }

  @FunctionalInterface
  private interface WalkValueConsumer {
    void accept(String suffix, String value);
  }

  private static class InterfaceAccumulator {
    private String name;
    private String description;
    private String alias;
    private String adminStatus;
    private String operStatus;
    private String nominalSpeed;
  }

  record DiscoveryColumnSpec(String macro, String oid) {
  }

  private static Map<String, String> createWalkOidFieldAliases() {
    Map<String, String> aliases = new LinkedHashMap<>();
    aliases.put("1.3.6.1.4.1.2021.10.1.2", "laName");
    aliases.put("1.3.6.1.4.1.2021.10.1.3", "laLoad");
    aliases.put("1.3.6.1.4.1.2021.9.1.1", "index");
    aliases.put("1.3.6.1.4.1.2021.9.1.2", "dskPath");
    aliases.put("1.3.6.1.4.1.2021.9.1.3", "dskDevice");
    return Map.copyOf(aliases);
  }
}
