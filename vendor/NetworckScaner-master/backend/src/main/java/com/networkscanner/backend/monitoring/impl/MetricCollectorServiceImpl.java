package com.networkscanner.backend.monitoring.impl;



import com.networkscanner.backend.monitoring.api.MetricCollectorService;

import com.networkscanner.backend.monitoring.api.MonitoredDeviceItemService;

import com.networkscanner.backend.monitoring.api.MonitoringMetricsPublisher;

import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;

import com.networkscanner.backend.monitoring.api.ThresholdEvaluationService;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;

import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;

import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;

import com.networkscanner.backend.monitoring.util.MonitoringSnmpTemplateSupport;

import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;

import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;

import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;

import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;

import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;

import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;

import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;

import com.networkscanner.backend.network.scan.api.SnmpScanService;

import jakarta.annotation.PreDestroy;

import java.time.OffsetDateTime;

import java.util.ArrayList;

import java.util.HashSet;

import java.util.Iterator;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.Set;

import java.util.UUID;

import java.util.concurrent.CancellationException;

import java.util.concurrent.CompletionService;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ExecutionException;

import java.util.concurrent.ExecutorCompletionService;

import com.networkscanner.backend.util.concurrent.NamedExecutors;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Future;

import java.util.concurrent.TimeUnit;

import java.util.stream.Collectors;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Service;



/**

 * Per-device collection uses a fixed pool; threads are named {@code monitoring-collector-N} so they are

 * distinguishable from generic {@code pool-M-thread-N} pools in thread dumps.

 *

 * <p>When {@code monitoring.collector.pre-snmp-icmp.enabled=true}, each cycle runs ICMP reachability for all

 * devices first, then lightweight collection for unreachable hosts and full SNMP only for eligible devices.

 */

@Service

@ConditionalOnProperty(name = "monitoring.collector.enabled", havingValue = "true", matchIfMissing = true)

public class MetricCollectorServiceImpl implements MetricCollectorService {



  private static final Logger log = LoggerFactory.getLogger(MetricCollectorServiceImpl.class);



  /**

   * В Zabbix это internal item; у нас его не опрашивают исполнители, но триггеры шаблонов на него ссылаются.

   * Значение синтезируется после цикла SNMP: 1 при ответе агента (число, текст walk JSON или сырой {@code []}).

   */

  private static final String ZABBIX_SNMP_AVAILABLE_KEY = "zabbix[host,snmp,available]";

  private static final long COMPLETION_POLL_MS = 200L;



  private final MonitoredDeviceRepository deviceRepository;

  private final MonitoringTemplateResolver templateResolver;

  private final SnmpScanService snmpScanService;

  private final ThresholdEvaluationService thresholdEvaluationService;

  private final ZabbixRuntimeStateService runtimeStateService;

  private final MonitoringMetricsPublisher metricsPublisher;

  private final MonitoredDeviceItemService monitoredDeviceItemService;

  private final SnmpMonitoringItemExecutor snmpMonitoringItemExecutor;

  private final IcmpMonitoringItemExecutor icmpMonitoringItemExecutor;

  private final List<MonitoringItemExecutor> itemExecutors;

  private final ExecutorService executor;

  private final ExecutorService icmpReachabilityExecutor;

  private final boolean kafkaEnabled;

  private final long perDeviceTimeoutMs;

  private final long lightweightPerDeviceTimeoutMs;

  private final boolean preSnmpIcmpEnabled;

  private final int preSnmpIcmpTimeoutMs;

  private final PreSnmpFailPolicy preSnmpFailPolicy;

  private final int preSnmpSnmpProbeTimeoutMs;

  private final long phase1PerDeviceBudgetMs;

  private final ConcurrentHashMap<Long, Object> deviceCollectLocks = new ConcurrentHashMap<>();



  public MetricCollectorServiceImpl(

      MonitoredDeviceRepository deviceRepository,

      MonitoringTemplateResolver templateResolver,

      SnmpScanService snmpScanService,

      ThresholdEvaluationService thresholdEvaluationService,

      ZabbixRuntimeStateService runtimeStateService,

      MonitoringMetricsPublisher metricsPublisher,

      MonitoredDeviceItemService monitoredDeviceItemService,

      IcmpMonitoringItemExecutor icmpMonitoringItemExecutor,

      SnmpMonitoringItemExecutor snmpMonitoringItemExecutor,

      DerivedMonitoringItemExecutor derivedMonitoringItemExecutor,

      @Value("${monitoring.kafka.enabled:false}") boolean kafkaEnabled,

      @Value("${monitoring.collector.per-device-timeout-ms:15000}") long perDeviceTimeoutMs,

      @Value("${monitoring.collector-threads:10}") int threads,

      @Value("${monitoring.collector.pre-snmp-icmp.enabled:true}") boolean preSnmpIcmpEnabled,

      @Value("${monitoring.collector.pre-snmp-icmp.timeout-ms:3000}") int preSnmpIcmpTimeoutMs,

      @Value("${monitoring.collector.pre-snmp-icmp.threads:${monitoring.collector-threads:10}}") int preSnmpIcmpThreads,

      @Value("${monitoring.collector.pre-snmp-icmp.fail-policy:snmp_probe}") String preSnmpFailPolicy,

      @Value("${monitoring.collector.pre-snmp-snmp-probe.timeout-ms:1500}") int preSnmpSnmpProbeTimeoutMs,

      @Value("${monitoring.collector.lightweight.per-device-timeout-ms:8000}") long lightweightPerDeviceTimeoutMs

  ) {

    this.deviceRepository = deviceRepository;

    this.templateResolver = templateResolver;

    this.snmpScanService = snmpScanService;

    this.thresholdEvaluationService = thresholdEvaluationService;

    this.runtimeStateService = runtimeStateService;

    this.metricsPublisher = metricsPublisher;

    this.monitoredDeviceItemService = monitoredDeviceItemService;

    this.snmpMonitoringItemExecutor = snmpMonitoringItemExecutor;

    this.icmpMonitoringItemExecutor = icmpMonitoringItemExecutor;

    this.itemExecutors = List.of(icmpMonitoringItemExecutor, snmpMonitoringItemExecutor, derivedMonitoringItemExecutor);

    this.kafkaEnabled = kafkaEnabled;

    this.perDeviceTimeoutMs = Math.max(perDeviceTimeoutMs, 1L);

    this.lightweightPerDeviceTimeoutMs = Math.max(lightweightPerDeviceTimeoutMs, 1L);

    this.preSnmpIcmpEnabled = preSnmpIcmpEnabled;

    this.preSnmpIcmpTimeoutMs = Math.max(preSnmpIcmpTimeoutMs, 1);

    this.preSnmpFailPolicy = PreSnmpFailPolicy.fromConfig(preSnmpFailPolicy);

    this.preSnmpSnmpProbeTimeoutMs = Math.max(preSnmpSnmpProbeTimeoutMs, 1);

    this.phase1PerDeviceBudgetMs = this.preSnmpIcmpTimeoutMs

        + (this.preSnmpFailPolicy == PreSnmpFailPolicy.SNMP_PROBE ? this.preSnmpSnmpProbeTimeoutMs : 0L)

        + 500L;

    this.executor = NamedExecutors.newFixedThreadPool(threads, "monitoring-collector-");

    this.icmpReachabilityExecutor = NamedExecutors.newFixedThreadPool(

        Math.max(preSnmpIcmpThreads, 1),

        "monitoring-collector-icmp-"

    );

  }



  @Override

  @Scheduled(fixedDelayString = "${monitoring.collect-interval-ms:30000}")

  public void collectAll() {

    List<MonitoredDeviceEntity> devices = deviceRepository.findAll();

    if (devices.isEmpty()) {

      return;

    }



    log.info("Metric collection started for {} device(s)", devices.size());

    OffsetDateTime now = OffsetDateTime.now();

    Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache = new ConcurrentHashMap<>();

    long startedAt = System.currentTimeMillis();



    Set<Long> snmpEligibleIds;

    List<MonitoredDeviceEntity> unreachableDevices;

    if (preSnmpIcmpEnabled) {

      Phase1ReachabilityResult reachability = runPhase1Reachability(devices, templateCache);

      snmpEligibleIds = reachability.snmpEligibleIds();

      unreachableDevices = reachability.unreachableDevices();

      log.info(

          "Metric collection phase1 (ICMP gate): devices={}, snmp_eligible={}, unreachable={}",

          devices.size(),

          snmpEligibleIds.size(),

          unreachableDevices.size()

      );

    } else {

      snmpEligibleIds = devices.stream()

          .map(MonitoredDeviceEntity::getId)

          .filter(id -> id != null)

          .collect(Collectors.toSet());

      unreachableDevices = List.of();

    }



    CollectCycleStats lightweightStats = runLightweightCollection(unreachableDevices, now, templateCache);

    List<MonitoredDeviceEntity> snmpDevices = devices.stream()

        .filter(device -> device.getId() != null && snmpEligibleIds.contains(device.getId()))

        .toList();

    CollectCycleStats snmpStats = runFullSnmpCollection(snmpDevices, now, templateCache);



    log.info(

        "Metric collection cycle completed: devices={}, snmp_eligible={}, lightweight_devices={}, "

            + "snmp_success={}, snmp_timeout={}, lightweight_success={}, lightweight_timeout={}, "

            + "kafka_failed={}, other_failed={}, durationMs={}",

        devices.size(),

        snmpEligibleIds.size(),

        unreachableDevices.size(),

        snmpStats.successCount(),

        snmpStats.timeoutCount(),

        lightweightStats.successCount(),

        lightweightStats.timeoutCount(),

        snmpStats.kafkaFailedCount() + lightweightStats.kafkaFailedCount(),

        snmpStats.failedCount() + lightweightStats.failedCount(),

        System.currentTimeMillis() - startedAt

    );

  }



  private Phase1ReachabilityResult runPhase1Reachability(

      List<MonitoredDeviceEntity> devices,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    CompletionService<ReachabilityCheckResult> completionService =

        new ExecutorCompletionService<>(icmpReachabilityExecutor);

    Map<Future<ReachabilityCheckResult>, PendingReachabilityTask> pending = new LinkedHashMap<>();



    for (MonitoredDeviceEntity device : devices) {

      Future<ReachabilityCheckResult> future = completionService.submit(

          () -> new ReachabilityCheckResult(device.getId(), isSnmpEligible(device, templateCache))

      );

      pending.put(future, new PendingReachabilityTask(device, phase1PerDeviceBudgetMs));

    }



    Set<Long> snmpEligibleIds = new HashSet<>();

    List<MonitoredDeviceEntity> unreachableDevices = new ArrayList<>();

    try {

      while (!pending.isEmpty()) {

        Future<ReachabilityCheckResult> completed = completionService.poll(

            nextReachabilityPollWaitMs(pending),

            TimeUnit.MILLISECONDS

        );

        if (completed != null) {

          PendingReachabilityTask task = pending.remove(completed);

          if (task != null) {

            try {

              ReachabilityCheckResult result = completed.get();

              if (result.snmpEligible()) {

                snmpEligibleIds.add(result.deviceId());

              } else {

                unreachableDevices.add(task.device());

              }

            } catch (ExecutionException exception) {

              log.warn(

                  "ICMP reachability check failed for device {}: {}",

                  task.device().getIp(),

                  exceptionSummary(exception.getCause() == null ? exception : exception.getCause())

              );

              unreachableDevices.add(task.device());

            }

          }

        }



        long nowNanos = System.nanoTime();

        Iterator<Map.Entry<Future<ReachabilityCheckResult>, PendingReachabilityTask>> iterator =

            pending.entrySet().iterator();

        while (iterator.hasNext()) {

          Map.Entry<Future<ReachabilityCheckResult>, PendingReachabilityTask> entry = iterator.next();

          if (entry.getValue().deadlineNanos() > nowNanos) {

            continue;

          }

          entry.getKey().cancel(true);

          unreachableDevices.add(entry.getValue().device());

          log.warn("ICMP reachability timed out for device {}", entry.getValue().device().getIp());

          iterator.remove();

        }

      }

    } catch (InterruptedException exception) {

      Thread.currentThread().interrupt();

      log.warn("Metric collection phase1 interrupted");

      for (Map.Entry<Future<ReachabilityCheckResult>, PendingReachabilityTask> entry : pending.entrySet()) {

        entry.getKey().cancel(true);

        unreachableDevices.add(entry.getValue().device());

      }

    }



    return new Phase1ReachabilityResult(Set.copyOf(snmpEligibleIds), List.copyOf(unreachableDevices));

  }



  private boolean isSnmpEligible(

      MonitoredDeviceEntity device,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    if (snmpScanService.checkIcmpReachable(device.getIp(), preSnmpIcmpTimeoutMs)) {

      return true;

    }

    if (preSnmpFailPolicy == PreSnmpFailPolicy.SKIP_ONLY) {

      return false;

    }

    ResolvedMonitoringTemplate template = resolveTemplateForDevice(device, templateCache);

    if (template == null) {

      return false;

    }

    ResolvedMonitoringTemplate probeTemplate = MonitoringSnmpTemplateSupport.withSnmpProbeTimeouts(

        applyDeviceSnmpOverrides(template, device),

        preSnmpSnmpProbeTimeoutMs,

        0

    );

    return snmpScanService.checkSnmpReachable(device.getIp(), probeTemplate);

  }



  private CollectCycleStats runLightweightCollection(

      List<MonitoredDeviceEntity> devices,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    if (devices.isEmpty()) {

      return CollectCycleStats.empty();

    }

    return runDeviceTasks(

        devices,

        timestamp,

        templateCache,

        lightweightPerDeviceTimeoutMs,

        device -> collectDeviceUnreachable(device, timestamp, templateCache)

    );

  }



  private CollectCycleStats runFullSnmpCollection(

      List<MonitoredDeviceEntity> devices,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    if (devices.isEmpty()) {

      return CollectCycleStats.empty();

    }

    return runDeviceTasks(

        devices,

        timestamp,

        templateCache,

        perDeviceTimeoutMs,

        device -> collectDevice(device, timestamp, templateCache)

    );

  }



  private CollectCycleStats runDeviceTasks(

      List<MonitoredDeviceEntity> devices,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache,

      long timeoutMs,

      DeviceCollectAction action

  ) {

    CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);

    Map<Future<Void>, PendingCollectTask> pending = new LinkedHashMap<>();

    int successCount = 0;

    int timeoutCount = 0;

    int kafkaFailedCount = 0;

    int failedCount = 0;



    for (MonitoredDeviceEntity device : devices) {

      PendingCollectTask task = new PendingCollectTask(device);

      Future<Void> future = completionService.submit(() -> {

        task.armDeadline(timeoutMs);

        action.run(device);

        return null;

      });

      pending.put(future, task);

    }



    try {

      while (!pending.isEmpty()) {

        Future<Void> completed = completionService.poll(nextPollWaitMs(pending), TimeUnit.MILLISECONDS);

        if (completed != null) {

          PendingCollectTask task = pending.remove(completed);

          if (task != null) {

            try {

              completed.get();

              successCount++;

            } catch (CancellationException exception) {

              timeoutCount++;

              log.warn("Metric collection timed out for device {}", task.device().getIp());

            } catch (ExecutionException exception) {

              Throwable cause = exception.getCause() == null ? exception : exception.getCause();

              if (isInterruptedFailure(cause)) {

                timeoutCount++;

                log.warn(

                    "Metric collection interrupted for device {}: {}",

                    task.device().getIp(),

                    exceptionSummary(cause)

                );

              } else if (isKafkaFailure(cause)) {

                kafkaFailedCount++;

                log.warn(

                    "Metric collection Kafka failed for device {}: {}",

                    task.device().getIp(),

                    exceptionSummary(cause)

                );

              } else {

                failedCount++;

                log.warn(

                    "Metric collection failed for device {}: {}",

                    task.device().getIp(),

                    exceptionSummary(cause),

                    cause

                );

              }

            }

          }

        }



        long nowNanos = System.nanoTime();

        Iterator<Map.Entry<Future<Void>, PendingCollectTask>> iterator = pending.entrySet().iterator();

        while (iterator.hasNext()) {

          Map.Entry<Future<Void>, PendingCollectTask> entry = iterator.next();

          PendingCollectTask task = entry.getValue();

          if (!task.isDeadlineArmed() || task.deadlineNanos() > nowNanos) {

            continue;

          }

          entry.getKey().cancel(false);

          timeoutCount++;

          log.warn("Metric collection timed out for device {}", task.device().getIp());

          iterator.remove();

        }

      }

    } catch (InterruptedException exception) {

      Thread.currentThread().interrupt();

      log.warn("Metric collection cycle interrupted");

      for (Future<Void> future : pending.keySet()) {

        future.cancel(false);

      }

    }



    return new CollectCycleStats(successCount, timeoutCount, kafkaFailedCount, failedCount);

  }



  private void collectDevice(

      MonitoredDeviceEntity device,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    if (device == null) {

      throw new IllegalStateException("Monitored device is null");

    }

    if (device.getId() == null) {

      throw new IllegalStateException("Monitored device id is null for ip " + device.getIp());

    }

    Object deviceLock = deviceCollectLocks.computeIfAbsent(device.getId(), ignored -> new Object());

    synchronized (deviceLock) {

      collectDeviceUnderLock(device, timestamp, templateCache);

    }

  }



  private void collectDeviceUnreachable(

      MonitoredDeviceEntity device,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    if (device == null) {

      throw new IllegalStateException("Monitored device is null");

    }

    if (device.getId() == null) {

      throw new IllegalStateException("Monitored device id is null for ip " + device.getIp());

    }

    Object deviceLock = deviceCollectLocks.computeIfAbsent(device.getId(), ignored -> new Object());

    synchronized (deviceLock) {

      collectDeviceUnreachableUnderLock(device, timestamp, templateCache);

    }

  }



  private void collectDeviceUnreachableUnderLock(

      MonitoredDeviceEntity device,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    ResolvedMonitoringTemplate template = resolveTemplateForDevice(device, templateCache);

    if (template == null) {

      throw new IllegalStateException("No monitoring template resolved for device " + device.getIp());

    }

    template = applyDeviceSnmpOverrides(template, device);



    java.util.Set<MonitoredDeviceItemService.ItemActivationKey> activeKeys =

        device.isItemAllowlistInitialized()

            ? monitoredDeviceItemService.loadActivationKeys(device.getId())

            : null;



    Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances =

        defaultDiscoveryInstances(runtimeStateService.loadActiveDiscoveryInstances(device));

    Map<String, ItemStateSnapshot> state = defaultItemState(runtimeStateService.loadItemState(device));

    List<MaterializedZabbixItem> dueItems = collectDueItems(template, discoveryInstances, state, timestamp, activeKeys);

    PollItemsResult pollResult = pollItems(device, template, dueItems, timestamp, state, false);

    List<ZabbixItemValue> values = appendZabbixSnmpAvailabilityItem(

        device,

        template,

        dueItems,

        pollResult.values(),

        pollResult.snmpRawByMetricName(),

        0.0d

    );

    publishOrPersistCollection(device, template, discoveryInstances, values, state, timestamp);

  }



  private void collectDeviceUnderLock(

      MonitoredDeviceEntity device,

      OffsetDateTime timestamp,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    ResolvedMonitoringTemplate template = resolveTemplateForDevice(device, templateCache);

    if (template == null) {

      throw new IllegalStateException("No monitoring template resolved for device " + device.getIp());

    }

    template = applyDeviceSnmpOverrides(template, device);



    java.util.Set<MonitoredDeviceItemService.ItemActivationKey> activeKeys =

        device.isItemAllowlistInitialized()

            ? monitoredDeviceItemService.loadActivationKeys(device.getId())

            : null;



    Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances =

        defaultDiscoveryInstances(runtimeStateService.loadActiveDiscoveryInstances(device));

    refreshDiscovery(device, template, discoveryInstances, timestamp);



    Map<String, ItemStateSnapshot> state = defaultItemState(runtimeStateService.loadItemState(device));

    List<MaterializedZabbixItem> dueItems = collectDueItems(template, discoveryInstances, state, timestamp, activeKeys);

    PollItemsResult pollResult = pollItems(device, template, dueItems, timestamp, state, true);

    List<ZabbixItemValue> values = appendZabbixSnmpAvailabilityItem(

        device,

        template,

        dueItems,

        pollResult.values(),

        pollResult.snmpRawByMetricName(),

        null

    );

    publishOrPersistCollection(device, template, discoveryInstances, values, state, timestamp);

  }



  private void publishOrPersistCollection(

      MonitoredDeviceEntity device,

      ResolvedMonitoringTemplate template,

      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,

      List<ZabbixItemValue> values,

      Map<String, ItemStateSnapshot> state,

      OffsetDateTime timestamp

  ) {

    if (kafkaEnabled) {

      if (!values.isEmpty()) {

        metricsPublisher.publish(new PolledMetricsEvent(

            UUID.randomUUID().toString(),

            template.schemaVersion(),

            device.getId(),

            device.getIp(),

            device.getVendor(),

            device.getModel(),

            template.id(),

            template.templateVersion(),

            template.packVersion(),

            timestamp,

            discoveryInstances,

            values,

            null,

            null,

            null

        ));

      }

      return;

    }



    if (!values.isEmpty()) {

      runtimeStateService.saveItemValues(

          device,

          template.id(),

          template.templateVersion(),

          template.packVersion(),

          values,

          timestamp

      );

      state = defaultItemState(runtimeStateService.loadItemState(device));

    }



    thresholdEvaluationService.evaluateTriggers(device, template, state, discoveryInstances, timestamp);

  }



  private ResolvedMonitoringTemplate resolveTemplateForDevice(

      MonitoredDeviceEntity device,

      Map<TemplateResolutionKey, ResolvedMonitoringTemplate> templateCache

  ) {

    List<String> selectedTemplateIds = MonitoringTemplateSelectionSupport.parseStored(

        device.getTemplateIds(),

        device.getTemplateId()

    );

    TemplateResolutionKey key = new TemplateResolutionKey(selectedTemplateIds, device.getVendor(), device.getModel());

    return templateCache.computeIfAbsent(

        key,

        ignored -> templateResolver.resolveForDevice(

            selectedTemplateIds,

            device.getVendor(),

            device.getModel(),

            device.getFirmwareVersion()

        )

    );

  }



  @Override

  @PreDestroy

  public void shutdown() {

    shutdownExecutor(executor);

    shutdownExecutor(icmpReachabilityExecutor);

  }



  private void shutdownExecutor(ExecutorService pool) {

    pool.shutdown();

    try {

      if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {

        pool.shutdownNow();

      }

    } catch (InterruptedException exception) {

      pool.shutdownNow();

      Thread.currentThread().interrupt();

    }

  }



  private void refreshDiscovery(

      MonitoredDeviceEntity device,

      ResolvedMonitoringTemplate template,

      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,

      OffsetDateTime timestamp

  ) {

    for (ZabbixDiscoveryRuleRuntime rule : safeValues(template.discoveryRules())) {

      List<DiscoveryInstanceRuntime> current = safeList(discoveryInstances.get(rule.key()));

      OffsetDateTime lastRun = current.stream()

          .map(DiscoveryInstanceRuntime::lastDiscoveredAt)

          .max(OffsetDateTime::compareTo)

          .orElse(null);

      if (!current.isEmpty() && !isDue(lastRun, rule.delaySeconds(), timestamp)) {

        continue;

      }

      List<DiscoveryInstanceRuntime> refreshed = snmpScanService.executeDiscovery(

          device.getIp(),

          template,

          rule,

          timestamp

      );

      refreshed = safeList(refreshed);

      runtimeStateService.replaceDiscoveryInstances(device, template.id(), rule.key(), refreshed);

      discoveryInstances.put(rule.key(), refreshed);

    }

  }



  private List<MaterializedZabbixItem> collectDueItems(

      ResolvedMonitoringTemplate template,

      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,

      Map<String, ItemStateSnapshot> state,

      OffsetDateTime timestamp,

      java.util.Set<MonitoredDeviceItemService.ItemActivationKey> activeKeys

  ) {

    List<MaterializedZabbixItem> dueItems = new ArrayList<>();

    for (ZabbixItemRuntime item : safeValues(template.items())) {

      if (!isItemActive(activeKeys, item.uuid(), "")) {

        continue;

      }

      ItemStateSnapshot snapshot = state.get(stateKey(item.key(), ""));

      if (isDue(snapshot == null ? null : snapshot.lastCollectedAt(), item.delaySeconds(), timestamp)) {

        dueItems.add(materializeItem(template, item, item.key(), "", null, Map.of()));

      }

    }

    for (ZabbixDiscoveryRuleRuntime rule : safeValues(template.discoveryRules())) {

      for (DiscoveryInstanceRuntime instance : safeList(discoveryInstances.get(rule.key()))) {

        for (ZabbixItemRuntime prototype : safeList(rule.itemPrototypes())) {

          if (prototype == null) {

            continue;

          }

          if (!isItemActive(activeKeys, prototype.uuid(), instance.instanceKey())

              && !isItemActive(activeKeys, prototype.uuid(), "*")) {

            continue;

          }

          String itemKey = applyMacros(prototype.key(), instance.macros());

          ItemStateSnapshot snapshot = state.get(stateKey(itemKey, instance.instanceKey()));

          if (isDue(snapshot == null ? null : snapshot.lastCollectedAt(), prototype.delaySeconds(), timestamp)) {

            dueItems.add(materializeItem(

                template,

                prototype,

                itemKey,

                instance.instanceKey(),

                rule.key(),

                instance.macros()

            ));

          }

        }

      }

    }

    return dueItems;

  }



  private boolean isItemActive(

      java.util.Set<MonitoredDeviceItemService.ItemActivationKey> activeKeys,

      String itemUuid,

      String instanceKey

  ) {

    if (activeKeys == null) {

      return true;

    }

    if (itemUuid == null || itemUuid.isBlank()) {

      return false;

    }

    String normalizedInstance = instanceKey == null ? "" : instanceKey;

    return activeKeys.contains(new MonitoredDeviceItemService.ItemActivationKey(itemUuid, normalizedInstance));

  }



  private List<ZabbixItemValue> appendZabbixSnmpAvailabilityItem(

      MonitoredDeviceEntity device,

      ResolvedMonitoringTemplate template,

      List<MaterializedZabbixItem> dueItems,

      List<ZabbixItemValue> values,

      Map<String, String> snmpRawByMetricName,

      Double forcedAvailability

  ) {

    Map<String, ZabbixItemRuntime> templateItems = template.items() == null ? Map.of() : template.items();

    ZabbixItemRuntime internal = templateItems.get(ZABBIX_SNMP_AVAILABLE_KEY);

    if (internal == null || !"INTERNAL".equalsIgnoreCase(internal.type())) {

      return values;

    }

    List<MaterializedZabbixItem> snmpAgentDue = safeList(dueItems).stream()

        .filter(MetricCollectorServiceImpl::isSchedulableSnmpAgentItem)

        .toList();

    if (snmpAgentDue.isEmpty() && forcedAvailability == null) {

      return values;

    }

    List<ZabbixItemValue> safeValues = safeList(values);

    if (safeValues.stream().anyMatch(v -> ZABBIX_SNMP_AVAILABLE_KEY.equals(v.itemKey()))) {

      return values;

    }

    double availability;

    if (forcedAvailability != null) {

      availability = forcedAvailability;

    } else {

      boolean snmpAvailable = resolveSnmpAgentAvailability(snmpAgentDue, snmpRawByMetricName, safeValues);

      if (!snmpAvailable) {

        snmpAvailable = snmpScanService.checkSnmpReachable(device.getIp(), template);

      }

      availability = snmpAvailable ? 1.0d : 0.0d;

    }

    List<ZabbixItemValue> merged = new ArrayList<>(safeValues);

    merged.add(

        new ZabbixItemValue(

            sourceTemplateIdForItem(template, internal),

            ZABBIX_SNMP_AVAILABLE_KEY,

            ZABBIX_SNMP_AVAILABLE_KEY,

            "",

            null,

            internal.uuid(),

            availability,

            null,

            null,

            internal.valueMapName(),

            null,

            null

        )

    );

    return List.copyOf(merged);

  }



  static boolean resolveSnmpAgentAvailability(

      List<MaterializedZabbixItem> snmpAgentDue,

      Map<String, String> snmpRawByMetricName,

      List<ZabbixItemValue> values

  ) {

    Set<String> snmpAgentUuids = safeList(snmpAgentDue).stream()

        .map(item -> item.runtime().uuid())

        .filter(uuid -> uuid != null && !uuid.isBlank())

        .collect(Collectors.toSet());

    boolean anyPollResult = safeList(values).stream()

        .anyMatch(

            v -> v.itemUuid() != null

                && snmpAgentUuids.contains(v.itemUuid())

                && (v.numericValue() != null || hasTextValue(v.textValue()))

        );

    if (anyPollResult) {

      return true;

    }

    Map<String, String> raw = snmpRawByMetricName == null ? Map.of() : snmpRawByMetricName;

    return snmpAgentDue.stream()

        .anyMatch(item -> {

          String response = raw.get(item.metricName());

          return response != null && !response.isBlank();

        });

  }



  private static boolean hasTextValue(String textValue) {

    return textValue != null && !textValue.isBlank();

  }



  private record PollItemsResult(List<ZabbixItemValue> values, Map<String, String> snmpRawByMetricName) {}



  private static boolean isSchedulableSnmpAgentItem(MaterializedZabbixItem item) {

    if (item == null || item.runtime() == null) {

      return false;

    }

    if (!"SNMP_AGENT".equalsIgnoreCase(item.runtime().type())) {

      return false;

    }

    String oid = item.snmpOid();

    return oid != null && !oid.isBlank() && !oid.contains("{#");

  }



  private PollItemsResult pollItems(

      MonitoredDeviceEntity device,

      ResolvedMonitoringTemplate template,

      List<MaterializedZabbixItem> items,

      OffsetDateTime timestamp,

      Map<String, ItemStateSnapshot> state,

      boolean includeSnmpAndDerived

  ) {

    List<MaterializedZabbixItem> safeItems = safeList(items);

    if (safeItems.isEmpty()) {

      return new PollItemsResult(List.of(), Map.of());

    }

    List<ZabbixItemValue> results = new ArrayList<>();

    Map<String, ZabbixItemValue> currentCycleValues = new LinkedHashMap<>();

    Map<String, String> snmpRawByMetricName = Map.of();

    for (MonitoringItemExecutor executor : itemExecutors) {

      if (!includeSnmpAndDerived && executor != icmpMonitoringItemExecutor) {

        continue;

      }

      List<MaterializedZabbixItem> supportedItems = safeItems.stream().filter(executor::supports).toList();

      if (supportedItems.isEmpty()) {

        continue;

      }

      if (executor == snmpMonitoringItemExecutor) {

        SnmpPollBatch batch = snmpMonitoringItemExecutor.executeBatch(

            device,

            template,

            supportedItems,

            state,

            timestamp

        );

        Map<String, String> batchRawByMetricName = batch == null || batch.rawByMetricName() == null

            ? Map.of()

            : batch.rawByMetricName();

        snmpRawByMetricName = batchRawByMetricName;

        mergeWalkMastersIntoCurrentCycle(supportedItems, batchRawByMetricName, currentCycleValues);

        List<ZabbixItemValue> batchValues = batch == null ? List.of() : safeList(batch.values());

        results.addAll(batchValues);

        for (ZabbixItemValue value : batchValues) {

          currentCycleValues.put(stateKey(value.itemKey(), value.instanceKey()), value);

        }

        continue;

      }

      List<ZabbixItemValue> executorResults = executor.execute(

          device,

          template,

          supportedItems,

          state,

          currentCycleValues,

          timestamp

      );

      executorResults = safeList(executorResults);

      results.addAll(executorResults);

      for (ZabbixItemValue value : executorResults) {

        currentCycleValues.put(stateKey(value.itemKey(), value.instanceKey()), value);

      }

    }

    return new PollItemsResult(List.copyOf(results), snmpRawByMetricName);

  }



  void mergeWalkMastersIntoCurrentCycle(

      List<MaterializedZabbixItem> snmpItems,

      Map<String, String> rawByMetricName,

      Map<String, ZabbixItemValue> currentCycleValues

  ) {

    for (MaterializedZabbixItem item : safeList(snmpItems)) {

      if (item == null || item.runtime() == null) {

        continue;

      }

      String oid = item.snmpOid();

      if (oid == null || !oid.contains("walk[")) {

        continue;

      }

      String raw = rawByMetricName.get(item.metricName());

      if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {

        continue;

      }

      currentCycleValues.put(

          stateKey(item.key(), item.instanceKey()),

          new ZabbixItemValue(

              item.templateId(),

              item.metricName(),

              item.key(),

              item.instanceKey(),

              item.discoveryRuleKey(),

              item.runtime().uuid(),

              null,

              raw,

              blankToNull(item.runtime().units()),

              item.runtime().valueMapName(),

              "ok",

              null

          )

      );

    }

  }



  private String blankToNull(String value) {

    return value == null || value.isBlank() ? null : value;

  }



  private MaterializedZabbixItem materializeItem(

      ResolvedMonitoringTemplate template,

      ZabbixItemRuntime runtime,

      String itemKey,

      String instanceKey,

      String discoveryRuleKey,

      Map<String, String> macros

  ) {

    Map<String, String> safeMacros = macros == null ? Map.of() : macros;

    return new MaterializedZabbixItem(

        sourceTemplateIdForItem(template, runtime),

        runtime,

        itemKey,

        itemKey,

        instanceKey,

        discoveryRuleKey,

        runtime == null || runtime.snmpOid() == null ? null : applyMacros(runtime.snmpOid(), safeMacros),

        safeMacros

    );

  }



  private String applyMacros(String value, Map<String, String> macros) {

    if (value == null) {

      return null;

    }

    String result = value;

    for (Map.Entry<String, String> macro : (macros == null ? Map.<String, String>of() : macros).entrySet()) {

      result = result.replace(macro.getKey(), macro.getValue());

    }

    return result;

  }



  private boolean isDue(OffsetDateTime lastCollectedAt, int delaySeconds, OffsetDateTime now) {

    if (lastCollectedAt == null) {

      return true;

    }

    return lastCollectedAt.plusSeconds(Math.max(delaySeconds, 1)).isBefore(now)

        || lastCollectedAt.plusSeconds(Math.max(delaySeconds, 1)).isEqual(now);

  }



  private String stateKey(String itemKey, String instanceKey) {

    return itemKey + "::" + (instanceKey == null ? "" : instanceKey);

  }



  private String sourceTemplateIdForItem(ResolvedMonitoringTemplate template, ZabbixItemRuntime runtime) {

    if (template == null) {

      return null;

    }

    if (runtime == null || template.itemTemplateIds() == null) {

      return template.id();

    }

    return template.itemTemplateIds().getOrDefault(runtime.key(), template.id());

  }



  private Map<String, List<DiscoveryInstanceRuntime>> defaultDiscoveryInstances(

      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances

  ) {

    return discoveryInstances == null ? new LinkedHashMap<>() : new LinkedHashMap<>(discoveryInstances);

  }



  private Map<String, ItemStateSnapshot> defaultItemState(Map<String, ItemStateSnapshot> state) {

    return state == null ? Map.of() : state;

  }



  private static <T> List<T> safeList(List<T> values) {

    return values == null ? List.of() : values;

  }



  private static <K, V> java.util.Collection<V> safeValues(Map<K, V> values) {

    return values == null ? List.of() : values.values();

  }



  private ResolvedMonitoringTemplate applyDeviceSnmpOverrides(

      ResolvedMonitoringTemplate template,

      MonitoredDeviceEntity device

  ) {

    return MonitoringSnmpTemplateSupport.applyDeviceSnmpOverrides(template, device);

  }



  private long nextPollWaitMs(Map<Future<Void>, PendingCollectTask> pending) {

    long now = System.nanoTime();

    long nearestDeadlineNanos = pending.values().stream()

        .filter(PendingCollectTask::isDeadlineArmed)

        .mapToLong(PendingCollectTask::deadlineNanos)

        .min()

        .orElse(now + TimeUnit.MILLISECONDS.toNanos(COMPLETION_POLL_MS));

    long waitNanos = Math.max(nearestDeadlineNanos - now, 0L);

    return Math.max(1L, Math.min(TimeUnit.NANOSECONDS.toMillis(waitNanos), COMPLETION_POLL_MS));

  }



  private long nextReachabilityPollWaitMs(Map<Future<ReachabilityCheckResult>, PendingReachabilityTask> pending) {

    long now = System.nanoTime();

    long nearestDeadlineNanos = pending.values().stream()

        .mapToLong(PendingReachabilityTask::deadlineNanos)

        .min()

        .orElse(now + TimeUnit.MILLISECONDS.toNanos(COMPLETION_POLL_MS));

    long waitNanos = Math.max(nearestDeadlineNanos - now, 0L);

    return Math.max(1L, Math.min(TimeUnit.NANOSECONDS.toMillis(waitNanos), COMPLETION_POLL_MS));

  }



  private String exceptionSummary(Throwable exception) {

    String message = exception.getMessage();

    return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);

  }



  private boolean isKafkaFailure(Throwable exception) {

    Throwable current = exception;

    while (current != null) {

      String className = current.getClass().getName();

      String message = current.getMessage();

      if (className.startsWith("org.apache.kafka")

          || className.contains("Kafka")

          || (message != null && message.toLowerCase().contains("kafka"))) {

        return true;

      }

      current = current.getCause();

    }

    return false;

  }



  private boolean isInterruptedFailure(Throwable exception) {

    Throwable current = exception;

    while (current != null) {

      if (current instanceof InterruptedException || current instanceof CancellationException) {

        return true;

      }

      String className = current.getClass().getName();

      if ("org.apache.kafka.common.errors.InterruptException".equals(className)) {

        return true;

      }

      current = current.getCause();

    }

    return false;

  }



  @FunctionalInterface

  private interface DeviceCollectAction {

    void run(MonitoredDeviceEntity device);

  }



  private enum PreSnmpFailPolicy {

    SKIP_ONLY,

    SNMP_PROBE;



    static PreSnmpFailPolicy fromConfig(String value) {

      if (value == null) {

        return SNMP_PROBE;

      }

      return switch (value.trim().toLowerCase()) {

        case "skip_only", "skip-only", "skip" -> SKIP_ONLY;

        default -> SNMP_PROBE;

      };

    }

  }



  private record TemplateResolutionKey(

      List<String> templateIds,

      String vendor,

      String model

  ) {

  }



  private record ReachabilityCheckResult(long deviceId, boolean snmpEligible) {

  }



  private record Phase1ReachabilityResult(Set<Long> snmpEligibleIds, List<MonitoredDeviceEntity> unreachableDevices) {

  }



  private record CollectCycleStats(int successCount, int timeoutCount, int kafkaFailedCount, int failedCount) {

    static CollectCycleStats empty() {

      return new CollectCycleStats(0, 0, 0, 0);

    }

  }



  private static final class PendingCollectTask {

    private final MonitoredDeviceEntity device;

    private volatile long deadlineNanos;



    private PendingCollectTask(MonitoredDeviceEntity device) {

      this.device = device;

    }



    private MonitoredDeviceEntity device() {

      return device;

    }



    private boolean isDeadlineArmed() {

      return deadlineNanos > 0L;

    }



    private long deadlineNanos() {

      return deadlineNanos;

    }



    private void armDeadline(long timeoutMs) {

      deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(timeoutMs, 1L));

    }

  }



  private static final class PendingReachabilityTask {
    private final MonitoredDeviceEntity device;
    private final long deadlineNanos;

    private PendingReachabilityTask(MonitoredDeviceEntity device, long budgetMs) {
      this.device = device;
      this.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(budgetMs, 1L));
    }

    private MonitoredDeviceEntity device() {
      return device;
    }

    private long deadlineNanos() {
      return deadlineNanos;
    }
  }



}


