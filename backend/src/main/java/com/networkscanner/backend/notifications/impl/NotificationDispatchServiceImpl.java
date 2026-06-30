package com.networkscanner.backend.notifications.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import com.networkscanner.backend.notifications.dto.SmtpTestDraftRequest;
import com.networkscanner.backend.notifications.model.NotificationSubscriptionEntity;
import com.networkscanner.backend.notifications.model.SmtpSettingsEntity;
import com.networkscanner.backend.notifications.repository.NotificationSubscriptionRepository;
import com.networkscanner.backend.notifications.repository.SmtpSettingsRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatchServiceImpl.class);
  private static final long SMTP_ID = 1L;
  private static final String CHANNEL_SMTP = "SMTP";
  private static final String CHANNEL_TELEGRAM = "TELEGRAM";
  private static final String LOGO_CONTENT_ID = "wisla-logo-v3";
  private static final ClassPathResource LOGO_RESOURCE = new ClassPathResource("mail/wisla-logo-mail.png");

  private final NotificationSubscriptionRepository subscriptionRepository;
  private final SmtpSettingsRepository smtpSettingsRepository;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final ObjectMapper objectMapper;
  private final TelegramBotClient telegramBotClient;

  public NotificationDispatchServiceImpl(
      NotificationSubscriptionRepository subscriptionRepository,
      SmtpSettingsRepository smtpSettingsRepository,
      MonitoredDeviceRepository monitoredDeviceRepository,
      ObjectMapper objectMapper,
      TelegramBotClient telegramBotClient
  ) {
    this.subscriptionRepository = subscriptionRepository;
    this.smtpSettingsRepository = smtpSettingsRepository;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.objectMapper = objectMapper;
    this.telegramBotClient = telegramBotClient;
  }

  @Override
  @Transactional(readOnly = true)
  public void sendTestEmail(String recipientEmail, SmtpTestDraftRequest smtpDraft) {
    SmtpSettingsEntity smtp = resolveSmtpForTest(smtpDraft);
    if (!smtpReady(smtp)) {
      String reason = smtpReadinessError(smtp);
      log.warn("Тестовое SMTP-письмо не отправлено: {}", reason);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMTP не настроен: " + reason);
    }
    String to = blank(recipientEmail) ? smtp.getFromEmail() : recipientEmail.trim().toLowerCase(Locale.ROOT);
    sendMailOrThrow(
      smtp,
      to,
      "Тестовое SMTP-уведомление",
      "Тестовое сообщение NetworckScaner. Если вы получили это письмо, SMTP настроен корректно."
    );
  }

  @Override
  @Transactional(readOnly = true)
  public void notifyNewDevicesDiscovered(long scanJobId, String scanJobName, List<DeviceScanResult> devices) {
    if (devices == null || devices.isEmpty()) {
      return;
    }
    List<NotificationSubscriptionEntity> subscriptions = subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase(CHANNEL_SMTP);
    if (subscriptions.isEmpty()) {
      return;
    }
    SmtpSettingsEntity smtp = smtpSettingsRepository.findById(SMTP_ID).orElse(null);
    if (!smtpReady(smtp)) {
      return;
    }
    for (DeviceScanResult device : devices) {
      if (device == null || blank(device.ip())) {
        continue;
      }
      for (NotificationSubscriptionEntity subscription : subscriptions) {
        if (!"OPERATOR".equalsIgnoreCase(subscription.getNotificationKind())) {
          continue;
        }
        if (!isMatchingSubscriptionType(subscription.getSubscriptionType(), "DEVICE", "TAG_GROUP")) {
          continue;
        }
        if (!matchesEvent(subscription, "NEW_DEVICE_DISCOVERED")) {
          continue;
        }
        if (!matchesDeviceFilters(subscription, device)) {
          continue;
        }
        String subject = "Операторское уведомление: обнаружено новое устройство";
        String body = "Задача сканирования: " + (scanJobName == null ? ("id=" + scanJobId) : scanJobName) + "\n"
            + "IP: " + safe(device.ip()) + "\n"
            + "Имя: " + safe(device.name()) + "\n"
            + "Хост: " + safe(device.hostName()) + "\n"
            + "MAC: " + safe(device.macAddress()) + "\n"
            + "Производитель: " + safe(device.vendor()) + "\n"
            + "Модель: " + safe(device.model()) + "\n"
            + "Теги: " + String.join(", ", device.tags() == null ? List.of() : device.tags()) + "\n";
        sendMailSilently(smtp, subscription.getRecipientEmail(), subject, body);
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void notifyAdministrativeEvent(
      String actorLogin,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  ) {
    List<NotificationSubscriptionEntity> subscriptions = subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase(CHANNEL_SMTP);
    if (subscriptions.isEmpty()) {
      return;
    }
    SmtpSettingsEntity smtp = smtpSettingsRepository.findById(SMTP_ID).orElse(null);
    if (!smtpReady(smtp)) {
      return;
    }
    String eventCode = resolveAdminEventCode(category, action, target, details);
    MonitoringEventContext contextFromAudit = resolveContextFromAudit(target, details, eventCode);
    MonitoringEventContext adminContext = new MonitoringEventContext(
        contextFromAudit.deviceId(),
        contextFromAudit.deviceIp(),
        contextFromAudit.deviceName(),
        contextFromAudit.tags(),
        eventCode,
        null,
        null,
        normalizeNullable(actorLogin),
        category == null ? null : category.name(),
        action == null ? null : action.name(),
        normalizeNullable(target),
        normalizeNullable(details)
    );
    for (NotificationSubscriptionEntity subscription : subscriptions) {
      if ("ADMIN".equalsIgnoreCase(subscription.getNotificationKind())) {
        if (!matchesEvent(subscription, eventCode) && !matchesEvent(subscription, "ADMIN_ANY")) {
          continue;
        }
        if (!evaluateCustomCondition(subscription.getCustomCondition(), adminContext)) {
          continue;
        }
      } else if ("OPERATOR".equalsIgnoreCase(subscription.getNotificationKind())) {
        if (!isMatchingSubscriptionType(subscription.getSubscriptionType(), "DEVICE", "TAG_GROUP")) {
          continue;
        }
        if (!matchesEvent(subscription, eventCode)) {
          continue;
        }
        if (!matchesMonitoringFilters(subscription, contextFromAudit)) {
          continue;
        }
      } else {
        continue;
      }
      String subject = "Административное уведомление: " + eventCode;
      String body = "Пользователь: " + safe(actorLogin) + "\n"
          + "Категория: " + (category == null ? "-" : category.name()) + "\n"
          + "Действие: " + (action == null ? "-" : action.name()) + "\n"
          + "Объект: " + safe(target) + "\n"
          + "Детали: " + safe(details) + "\n";
      sendMailSilently(smtp, subscription.getRecipientEmail(), subject, body);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void notifyMonitoringEvent(
      Long deviceId,
      String deviceIp,
      String deviceName,
      Collection<String> deviceTags,
      MonitoringEventMutation mutation
  ) {
    if (mutation == null || mutation.action() == null) {
      return;
    }
    String eventCode = switch (mutation.action()) {
      case OPEN, UPDATE -> "MONITORING_EVENT_OPEN";
      case RESOLVE -> "MONITORING_EVENT_RESOLVED";
    };
    MonitoringEventContext context = new MonitoringEventContext(
        deviceId,
        safe(deviceIp),
        safe(deviceName),
        normalizeTagSet(deviceTags),
        eventCode,
        normalizeNullable(mutation.severity()),
        normalizeNullable(mutation.metricName()),
        null,
        null,
        null,
        null,
        null
    );
    dispatchMonitoringEventSmtp(context, mutation);
    dispatchMonitoringEventTelegram(context, mutation);
  }

  private void dispatchMonitoringEventSmtp(MonitoringEventContext context, MonitoringEventMutation mutation) {
    List<NotificationSubscriptionEntity> subscriptions =
        subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase(CHANNEL_SMTP);
    if (subscriptions.isEmpty()) {
      return;
    }
    SmtpSettingsEntity smtp = smtpSettingsRepository.findById(SMTP_ID).orElse(null);
    if (!smtpReady(smtp)) {
      return;
    }
    String subject = "Операторское уведомление: " + localizeEventCode(context.eventCode());
    String body = buildMonitoringEventBody(context, mutation);
    for (NotificationSubscriptionEntity subscription : subscriptions) {
      if (!matchesMonitoringSubscription(subscription, context)) {
        continue;
      }
      sendMailSilently(smtp, subscription.getRecipientEmail(), subject, body);
    }
  }

  private void dispatchMonitoringEventTelegram(MonitoringEventContext context, MonitoringEventMutation mutation) {
    if (!telegramBotClient.isReady()) {
      return;
    }
    List<NotificationSubscriptionEntity> subscriptions =
        subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase(CHANNEL_TELEGRAM);
    if (subscriptions.isEmpty()) {
      return;
    }
    String text = buildMonitoringEventBody(context, mutation);
    for (NotificationSubscriptionEntity subscription : subscriptions) {
      if (!matchesMonitoringSubscription(subscription, context)) {
        continue;
      }
      telegramBotClient.sendMessageSilently(subscription.getRecipientEmail(), text);
    }
  }

  private boolean matchesMonitoringSubscription(
      NotificationSubscriptionEntity subscription,
      MonitoringEventContext context
  ) {
    if (!"OPERATOR".equalsIgnoreCase(subscription.getNotificationKind())) {
      return false;
    }
    if (!isMatchingSubscriptionType(subscription.getSubscriptionType(), "DEVICE", "TAG_GROUP")) {
      return false;
    }
    if (!matchesEvent(subscription, context.eventCode())) {
      return false;
    }
    return matchesMonitoringFilters(subscription, context);
  }

  private String buildMonitoringEventBody(MonitoringEventContext context, MonitoringEventMutation mutation) {
    MonitoredDeviceEntity device = resolveMonitoredDevice(context);
    boolean armWorkstation = isArmWorkstation(context, device);

    String actionLabel = localizeEventCode(context.eventCode());
    String headerSymbol = "MONITORING_EVENT_RESOLVED".equals(context.eventCode()) ? "✅" : "🔴";
    String header = armWorkstation
        ? headerSymbol + " WISLA АРМ — " + actionLabel
        : headerSymbol + " wiSLA — " + actionLabel;

    String hostLabel = armWorkstation ? "Рабочая станция" : "Устройство";
    String hostname = context.deviceName();
    if (device != null && !blank(device.getHostName())) {
      hostname = device.getHostName().trim();
    }

    StringBuilder body = new StringBuilder();
    body.append(header).append("\n\n");
    body.append(hostLabel).append(": ").append(safe(hostname)).append("\n");
    body.append("IP: ").append(safe(context.deviceIp())).append("\n");

    if (armWorkstation && device != null) {
      if (!blank(device.getModel())) {
        body.append("ОС: ").append(device.getModel().trim()).append("\n");
      }
      if (!blank(device.getFirmwareVersion()) && !"-".equals(device.getFirmwareVersion().trim())) {
        body.append("Агент: ").append(device.getFirmwareVersion().trim()).append("\n");
      }
    }

    String triggerName = mutation.triggerName() != null && !mutation.triggerName().isBlank()
        ? mutation.triggerName()
        : safe(context.metricName());
    body.append("\nТриггер: ").append(triggerName).append("\n");

    String metricName = !blank(mutation.metricName()) ? mutation.metricName() : context.metricName();
    if (!blank(metricName)) {
      body.append("Метрика: ").append(metricName).append("\n");
    }
    if ("MONITORING_EVENT_RESOLVED".equals(context.eventCode())) {
      body.append("Статус: метрика вернулась в норму, порог больше не нарушен\n");
    } else if (mutation.actualValue() > 0 || mutation.thresholdValue() > 0) {
      body.append("Значение: ")
          .append(formatMonitoringMetricValue(metricName, mutation.actualValue(), mutation.thresholdValue()))
          .append("\n");
    }

    body.append("Критичность: ").append(localizeSeverity(context.severity()));

    if (!armWorkstation && context.tags() != null && !context.tags().isEmpty()) {
      body.append("\nТеги: ").append(String.join(", ", context.tags()));
    }

    return body.toString();
  }

  private MonitoredDeviceEntity resolveMonitoredDevice(MonitoringEventContext context) {
    if (context.deviceId() == null) {
      return null;
    }
    return monitoredDeviceRepository.findById(context.deviceId()).orElse(null);
  }

  private static boolean isArmWorkstation(MonitoringEventContext context, MonitoredDeviceEntity device) {
    if (context.tags() != null && context.tags().stream().anyMatch(tag -> "arm-workstation".equalsIgnoreCase(tag))) {
      return true;
    }
    if (device == null) {
      return false;
    }
    if ("WISLA ARM".equalsIgnoreCase(device.getVendor())) {
      return true;
    }
    String tagsJson = device.getTagsJson();
    return tagsJson != null && tagsJson.toLowerCase(Locale.ROOT).contains("arm-workstation");
  }

  private static String formatMonitoringMetricValue(String metricName, double actual, double threshold) {
    if (metricName != null && (metricName.contains("used_pct") || metricName.contains("cpu.util"))) {
      return String.format(Locale.ROOT, "%.1f%% (порог %.0f%%)", actual, threshold);
    }
    if (metricName != null && metricName.contains("mem.used")) {
      return String.format(Locale.ROOT, "%.0f (порог %.0f)", actual, threshold);
    }
    return String.format(Locale.ROOT, "%.2f (порог %.2f)", actual, threshold);
  }

  @Override
  @Transactional(readOnly = true)
  public void notifyScanJobEvent(long scanJobId, String scanJobName, String eventCode, String details) {
    if (blank(eventCode)) {
      return;
    }
    List<NotificationSubscriptionEntity> subscriptions = subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase(CHANNEL_SMTP);
    if (subscriptions.isEmpty()) {
      return;
    }
    SmtpSettingsEntity smtp = smtpSettingsRepository.findById(SMTP_ID).orElse(null);
    if (!smtpReady(smtp)) {
      return;
    }
    for (NotificationSubscriptionEntity subscription : subscriptions) {
      if (!"OPERATOR".equalsIgnoreCase(subscription.getNotificationKind())) {
        continue;
      }
      if (!isMatchingSubscriptionType(subscription.getSubscriptionType(), "SCAN_JOB")) {
        continue;
      }
      if (!matchesEvent(subscription, eventCode)) {
        continue;
      }
      String subject = "Операторское уведомление по сканированию: " + localizeEventCode(eventCode);
      String body = "Задача: " + safe(scanJobName) + " (id=" + scanJobId + ")\n"
          + "Событие: " + eventCode + "\n"
          + "Детали: " + safe(details) + "\n";
      sendMailSilently(smtp, subscription.getRecipientEmail(), subject, body);
    }
  }

  private boolean smtpReady(SmtpSettingsEntity smtp) {
    return smtp != null && smtp.isEnabled() && !blank(smtp.getServerHost()) && smtp.getServerPort() != null && !blank(smtp.getFromEmail());
  }

  private SmtpSettingsEntity resolveSmtpForTest(SmtpTestDraftRequest draft) {
    if (draft == null) {
      return smtpSettingsRepository.findById(SMTP_ID).orElse(null);
    }
    SmtpSettingsEntity persisted = smtpSettingsRepository.findById(SMTP_ID).orElse(null);
    SmtpSettingsEntity resolved = new SmtpSettingsEntity();
    resolved.setEnabled(boolOrDefault(draft.enabled(), persisted == null || persisted.isEnabled()));
    resolved.setServerHost(stringOrDefault(draft.serverHost(), persisted == null ? null : persisted.getServerHost()));
    resolved.setServerPort(intOrDefault(draft.serverPort(), persisted == null ? null : persisted.getServerPort()));
    resolved.setAuth(boolOrDefault(draft.auth(), persisted != null && persisted.isAuth()));
    resolved.setStarttls(boolOrDefault(draft.starttls(), persisted != null && persisted.isStarttls()));
    resolved.setSsl(boolOrDefault(draft.ssl(), persisted != null && persisted.isSsl()));
    resolved.setUsername(stringOrDefault(draft.username(), persisted == null ? null : persisted.getUsername()));
    resolved.setFromEmail(stringOrDefault(draft.fromEmail(), persisted == null ? null : persisted.getFromEmail()));
    if (Boolean.TRUE.equals(draft.clearPassword())) {
      resolved.setPassword(null);
    } else if (!blank(draft.password())) {
      resolved.setPassword(draft.password());
    } else {
      resolved.setPassword(persisted == null ? null : persisted.getPassword());
    }
    return resolved;
  }

  private static boolean boolOrDefault(Boolean value, boolean fallback) {
    return value == null ? fallback : value;
  }

  private static Integer intOrDefault(Integer value, Integer fallback) {
    return value == null ? fallback : value;
  }

  private static String stringOrDefault(String value, String fallback) {
    if (!blank(value)) {
      return value.trim();
    }
    return fallback == null ? null : fallback.trim();
  }

  private boolean matchesDeviceFilters(NotificationSubscriptionEntity subscription, DeviceScanResult device) {
    if (!blank(subscription.getDeviceIpFilter())) {
      List<String> ipRules = java.util.Arrays.stream(subscription.getDeviceIpFilter().split(","))
          .map(String::trim)
          .filter(v -> !v.isBlank())
          .distinct()
          .toList();
      String deviceIp = device.ip() == null ? "" : device.ip().trim();
      if (!matchesAnyIpRule(deviceIp, ipRules)) {
        return false;
      }
    }
    if (!blank(subscription.getDeviceTagFilter())) {
      List<String> tags = device.tags() == null ? List.of() : device.tags();
      List<String> requestedTags = java.util.Arrays.stream(subscription.getDeviceTagFilter().split(","))
          .map(String::trim)
          .filter(v -> !v.isBlank())
          .map(v -> v.toLowerCase(Locale.ROOT))
          .distinct()
          .toList();
      boolean matched = tags.stream()
          .filter(t -> t != null && !t.isBlank())
          .map(t -> t.trim().toLowerCase(Locale.ROOT))
          .anyMatch(requestedTags::contains);
      if (!matched) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesEvent(NotificationSubscriptionEntity subscription, String eventCode) {
    if (subscription == null || eventCode == null || eventCode.isBlank()) {
      return false;
    }
    String stored = subscription.getEventCode();
    if (stored == null || stored.isBlank()) {
      return false;
    }
    return java.util.Arrays.stream(stored.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .anyMatch(v -> v.equalsIgnoreCase(eventCode));
  }

  private boolean matchesMonitoringFilters(NotificationSubscriptionEntity subscription, MonitoringEventContext context) {
    if (context == null) {
      return true;
    }
    boolean eventSupportsMetricAndSeverity = context.eventCode() != null
        && !"DEVICE_UNMONITORED".equalsIgnoreCase(context.eventCode())
        && !"EQUIPMENT_CONFIG_CHANGED".equalsIgnoreCase(context.eventCode());
    if (eventSupportsMetricAndSeverity && !blank(subscription.getSeverityFilter())) {
      Set<String> allowed = splitCsvLower(subscription.getSeverityFilter());
      String severity = context.severity() == null ? "" : context.severity().toLowerCase(Locale.ROOT);
      if (!allowed.isEmpty() && !allowed.contains(severity)) {
        return false;
      }
    }
    if (eventSupportsMetricAndSeverity && !blank(subscription.getMetricFilter())) {
      Set<String> requiredMetrics = splitCsvLower(subscription.getMetricFilter());
      String metricName = context.metricName() == null ? "" : context.metricName().toLowerCase(Locale.ROOT);
      boolean metricMatched = requiredMetrics.isEmpty()
          || requiredMetrics.stream().anyMatch(metricName::contains);
      if (!metricMatched) {
        return false;
      }
    }
    if (!blank(subscription.getDeviceIpFilter()) && !matchesAnyIpRule(context.deviceIp(), List.copyOf(splitCsv(subscription.getDeviceIpFilter())))) {
      return false;
    }
    if (!blank(subscription.getDeviceTagFilter())) {
      Set<String> expectedTags = splitCsvLower(subscription.getDeviceTagFilter());
      boolean matched = context.tags().stream()
          .map(v -> v.toLowerCase(Locale.ROOT))
          .anyMatch(expectedTags::contains);
      if (!matched) {
        return false;
      }
    }
    return evaluateCustomCondition(subscription.getCustomCondition(), context);
  }

  private boolean matchesAnyIpRule(String deviceIp, List<String> rules) {
    if (deviceIp == null || deviceIp.isBlank() || rules == null || rules.isEmpty()) {
      return false;
    }
    long deviceIpLong = toIpv4Long(deviceIp);
    if (deviceIpLong < 0) {
      return false;
    }
    for (String rule : rules) {
      if (rule.contains("/")) {
        if (matchesCidr(deviceIpLong, rule)) {
          return true;
        }
        continue;
      }
      if (rule.contains("-")) {
        if (matchesRange(deviceIpLong, rule)) {
          return true;
        }
        continue;
      }
      long singleIp = toIpv4Long(rule);
      if (singleIp >= 0 && singleIp == deviceIpLong) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesRange(long deviceIpLong, String rule) {
    String[] parts = rule.split("-", 2);
    if (parts.length != 2) {
      return false;
    }
    long start = toIpv4Long(parts[0].trim());
    long end = toIpv4Long(parts[1].trim());
    if (start < 0 || end < 0) {
      return false;
    }
    long min = Math.min(start, end);
    long max = Math.max(start, end);
    return deviceIpLong >= min && deviceIpLong <= max;
  }

  private boolean matchesCidr(long deviceIpLong, String rule) {
    String[] parts = rule.split("/", 2);
    if (parts.length != 2) {
      return false;
    }
    long network = toIpv4Long(parts[0].trim());
    int prefixLength;
    try {
      prefixLength = Integer.parseInt(parts[1].trim());
    } catch (NumberFormatException exception) {
      return false;
    }
    if (network < 0 || prefixLength < 0 || prefixLength > 32) {
      return false;
    }
    long mask = prefixLength == 0 ? 0 : (-1L << (32 - prefixLength)) & 0xFFFF_FFFFL;
    return (deviceIpLong & mask) == (network & mask);
  }

  private long toIpv4Long(String ip) {
    if (ip == null || ip.isBlank()) {
      return -1;
    }
    String[] octets = ip.trim().split("\\.");
    if (octets.length != 4) {
      return -1;
    }
    long value = 0;
    for (String octet : octets) {
      int part;
      try {
        part = Integer.parseInt(octet);
      } catch (NumberFormatException exception) {
        return -1;
      }
      if (part < 0 || part > 255) {
        return -1;
      }
      value = (value << 8) | part;
    }
    return value;
  }

  private String resolveAdminEventCode(AuditCategory category, AuditAction action, String target, String details) {
    String t = target == null ? "" : target.toLowerCase(Locale.ROOT);
    String d = details == null ? "" : details.toLowerCase(Locale.ROOT);
    if (category == AuditCategory.USER_ADMIN && action == AuditAction.CREATE) {
      return "USER_CREATED";
    }
    if (category == AuditCategory.USER_ADMIN && action == AuditAction.UPDATE
        && d.contains("заблокирован")) {
      return "USER_BLOCKED";
    }
    if (t.contains("create_user")) {
      return "USER_CREATED";
    }
    if (t.contains("block_user")) {
      return "USER_BLOCKED";
    }
    if (category == AuditCategory.AUTH_SESSION && action == AuditAction.LOGIN) {
      return "USER_LOGIN";
    }
    if (category == AuditCategory.AUTH_SESSION && action == AuditAction.LOGIN_FAILED) {
      return "USER_LOGIN_FAILED";
    }
    if (category == AuditCategory.MONITORING_DEVICE && action == AuditAction.DELETE) {
      return "DEVICE_UNMONITORED";
    }
    if (category == AuditCategory.MONITORING_TEMPLATE && action == AuditAction.UPDATE) {
      return "TEMPLATE_CHANGED";
    }
    if (category == AuditCategory.MONITORING_TEMPLATE && action == AuditAction.CREATE) {
      return "TEMPLATE_CHANGED";
    }
    if (category == AuditCategory.MONITORING_TEMPLATE && action == AuditAction.DELETE) {
      return "TEMPLATE_CHANGED";
    }
    if (category == AuditCategory.MONITORING_DEVICE && action == AuditAction.UPDATE) {
      if (d.contains("теги") || d.contains("item мониторинга")) {
        return "MONITORING_SETTINGS_CHANGED";
      }
      if (d.contains("конфиг") || d.contains("configuration")) {
        return "EQUIPMENT_CONFIG_CHANGED";
      }
      return "MONITORING_SETTINGS_CHANGED";
    }
    if (category == AuditCategory.MONITORING_DEVICE && action == AuditAction.CREATE) {
      return "MONITORING_TEMPLATE_APPLIED_TO_DEVICE";
    }
    return "ADMIN_ANY";
  }

  private MonitoringEventContext resolveContextFromAudit(String target, String details, String eventCode) {
    String source = (target == null ? "" : target) + " " + (details == null ? "" : details);
    String ip = extractIpv4(source);
    Long deviceId = null;
    String deviceName = "-";
    Set<String> tags = Set.of();
    if (!blank(ip)) {
      MonitoredDeviceEntity device = monitoredDeviceRepository.findByIp(ip).orElse(null);
      if (device != null) {
        deviceId = device.getId();
        deviceName = safe(device.getName());
        tags = readTags(device.getTagsJson());
      }
    }
    return new MonitoringEventContext(deviceId, safe(ip), deviceName, tags, eventCode, null, null, null, null, null, null, null);
  }

  private Set<String> readTags(String tagsJson) {
    try {
      if (blank(tagsJson)) {
        return Set.of();
      }
      List<String> tags = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
      return normalizeTagSet(tags);
    } catch (Exception e) {
      return Set.of();
    }
  }

  private static Set<String> normalizeTagSet(Collection<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return Set.of();
    }
    return tags.stream()
        .filter(v -> v != null && !v.isBlank())
        .map(String::trim)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<String> splitCsvLower(String value) {
    return splitCsv(value).stream()
        .map(v -> v.toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private static Set<String> splitCsv(String value) {
    if (blank(value)) {
      return Set.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean evaluateCustomCondition(String customCondition, MonitoringEventContext context) {
    if (blank(customCondition)) {
      return true;
    }
    // Supports lightweight boolean expressions:
    // event=..., severity=..., metric=..., ip=..., tag=... with && and || and optional !.
    String normalized = customCondition.trim();
    String[] orGroups = normalized.split("\\|\\|");
    for (String group : orGroups) {
      boolean allMatch = true;
      String[] andTerms = group.split("&&");
      for (String rawTerm : andTerms) {
        String term = rawTerm.trim();
        if (term.isEmpty()) {
          continue;
        }
        boolean negated = term.startsWith("!");
        String expr = negated ? term.substring(1).trim() : term;
        boolean matched = evaluateConditionTerm(expr, context);
        if (negated) {
          matched = !matched;
        }
        if (!matched) {
          allMatch = false;
          break;
        }
      }
      if (allMatch) {
        return true;
      }
    }
    return false;
  }

  private boolean evaluateConditionTerm(String expression, MonitoringEventContext context) {
    int equalsIndex = expression.indexOf('=');
    if (equalsIndex <= 0 || equalsIndex >= expression.length() - 1) {
      return false;
    }
    String key = expression.substring(0, equalsIndex).trim().toLowerCase(Locale.ROOT);
    String value = expression.substring(equalsIndex + 1).trim().toLowerCase(Locale.ROOT);
    if (value.isBlank()) {
      return false;
    }
    return switch (key) {
      case "event", "eventcode" -> context.eventCode() != null
          && context.eventCode().toLowerCase(Locale.ROOT).contains(value);
      case "severity" -> context.severity() != null
          && context.severity().toLowerCase(Locale.ROOT).contains(value);
      case "metric", "metricname" -> context.metricName() != null
          && context.metricName().toLowerCase(Locale.ROOT).contains(value);
      case "ip", "deviceip" -> context.deviceIp() != null
          && context.deviceIp().toLowerCase(Locale.ROOT).contains(value);
      case "tag", "devicetag" -> context.tags().stream()
          .map(v -> v.toLowerCase(Locale.ROOT))
          .anyMatch(v -> v.contains(value));
      case "actor" -> context.actor() != null
          && context.actor().toLowerCase(Locale.ROOT).contains(value);
      case "category" -> context.category() != null
          && context.category().toLowerCase(Locale.ROOT).contains(value);
      case "action" -> context.action() != null
          && context.action().toLowerCase(Locale.ROOT).contains(value);
      case "target" -> context.target() != null
          && context.target().toLowerCase(Locale.ROOT).contains(value);
      case "details", "detail" -> context.details() != null
          && context.details().toLowerCase(Locale.ROOT).contains(value);
      default -> false;
    };
  }

  private static String extractIpv4(String input) {
    if (blank(input)) {
      return "";
    }
    java.util.regex.Matcher matcher = java.util.regex.Pattern
        .compile("\\b((25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(?!$)|$)){4}\\b")
        .matcher(input);
    return matcher.find() ? matcher.group() : "";
  }

  private static String normalizeNullable(String value) {
    return blank(value) ? null : value.trim();
  }

  private static boolean isMatchingSubscriptionType(String value, String... allowedTypes) {
    String actual = normalizeNullable(value);
    if (actual == null) {
      actual = "DEVICE";
    }
    for (String allowed : allowedTypes) {
      if (actual.equalsIgnoreCase(allowed)) {
        return true;
      }
    }
    return false;
  }

  private void sendMailSilently(SmtpSettingsEntity smtp, String to, String subject, String body) {
    if (blank(to)) {
      return;
    }
    for (String recipient : parseRecipients(to)) {
      try {
        doSendMail(smtp, recipient, subject, body);
      } catch (RuntimeException exception) {
        log.warn("Не удалось отправить SMTP-уведомление на {}: {}", recipient, exception.getMessage());
      }
    }
  }

  private void sendMailOrThrow(SmtpSettingsEntity smtp, String to, String subject, String body) {
    List<String> recipients = parseRecipients(to);
    if (recipients.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не указан получатель тестового письма.");
    }
    for (String recipient : recipients) {
      try {
        doSendMail(smtp, recipient, subject, body);
        log.info("Тестовое SMTP-письмо отправлено на {}", recipient);
      } catch (RuntimeException exception) {
        log.warn("Ошибка тестовой SMTP-отправки на {}: {}", recipient, exception.getMessage());
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "Ошибка SMTP-отправки: " + exception.getMessage(),
            exception
        );
      }
    }
  }

  private List<String> parseRecipients(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .map(v -> v.toLowerCase(Locale.ROOT))
        .distinct()
        .toList();
  }

  private void doSendMail(SmtpSettingsEntity smtp, String to, String subject, String body) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(smtp.getServerHost());
    sender.setPort(smtp.getServerPort());
    sender.setUsername(smtp.getUsername());
    sender.setPassword(smtp.getPassword());
    Properties props = sender.getJavaMailProperties();
    props.put("mail.smtp.auth", String.valueOf(smtp.isAuth()));
    props.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttls()));
    props.put("mail.smtp.ssl.enable", String.valueOf(smtp.isSsl()));
    try {
      var message = sender.createMimeMessage();
      var helper = new MimeMessageHelper(message, true, java.nio.charset.StandardCharsets.UTF_8.name());
      helper.setFrom(smtp.getFromEmail());
      helper.setTo(to.trim().toLowerCase(Locale.ROOT));
      helper.setSubject(subject);
      helper.setText(body, buildHtmlBody(subject, body));
      if (LOGO_RESOURCE.exists()) {
        helper.addInline(LOGO_CONTENT_ID, LOGO_RESOURCE, "image/png");
      }
      sender.send(message);
    } catch (Exception exception) {
      throw new IllegalStateException(exception.getMessage(), exception);
    }
  }

  private String smtpReadinessError(SmtpSettingsEntity smtp) {
    if (smtp == null) {
      return "запись SMTP не найдена в БД";
    }
    if (!smtp.isEnabled()) {
      return "SMTP выключен (enabled=false)";
    }
    if (blank(smtp.getServerHost())) {
      return "не указан SMTP сервер";
    }
    if (smtp.getServerPort() == null) {
      return "не указан SMTP порт";
    }
    if (blank(smtp.getFromEmail())) {
      return "не указан email отправителя";
    }
    return "неизвестная причина";
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private static String buildHtmlBody(String subject, String plainBody) {
    String escapedBody = java.util.Arrays.stream((plainBody == null ? "" : plainBody).split("\\R"))
        .map(NotificationDispatchServiceImpl::escapeHtml)
        .collect(Collectors.joining("<br/>"));
    String escapedSubject = escapeHtml(subject == null ? "" : subject);
    return "<!doctype html>"
        + "<html><body style=\"margin:0;padding:0;background:#f3f6fb;font-family:Arial,sans-serif;color:#111827;\">"
        + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"padding:24px 0;\">"
        + "<tr><td align=\"center\">"
        + "<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" "
        + "style=\"background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;\">"
        + "<tr><td style=\"padding:18px 24px;background-color:#0f172a;"
        + "background-image:linear-gradient(185deg,#161f30 0%,#0f172a 45%,#0b1220 100%);\">"
        + "<img src=\"cid:" + LOGO_CONTENT_ID + "\" alt=\"WISLA\" style=\"width:144px;height:auto;display:block;\"/>"
        + "</td></tr>"
        + "<tr><td style=\"padding:20px 24px;\">"
        + "<h2 style=\"margin:0 0 12px 0;font-size:18px;color:#111827;\">" + escapedSubject + "</h2>"
        + "<div style=\"font-size:14px;line-height:1.6;color:#1f2937;\">" + escapedBody + "</div>"
        + "</td></tr>"
        + "</table></td></tr></table></body></html>";
  }

  private static String escapeHtml(String input) {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static String localizeEventCode(String eventCode) {
    if (blank(eventCode)) {
      return "-";
    }
    return switch (eventCode.trim().toUpperCase(Locale.ROOT)) {
      case "NEW_DEVICE_DISCOVERED" -> "Обнаружено новое устройство";
      case "MONITORING_EVENT_OPEN" -> "Сработало событие мониторинга";
      case "MONITORING_EVENT_RESOLVED" -> "Событие мониторинга устранено";
      case "DEVICE_UNMONITORED" -> "Устройство снято с мониторинга";
      case "EQUIPMENT_CONFIG_CHANGED" -> "Изменен конфигурационный файл устройства";
      case "SCAN_JOB_SCHEDULED" -> "Запланированное сканирование запущено";
      case "SCAN_JOB_COMPLETED" -> "Сканирование завершено";
      case "SCAN_JOB_FAILED" -> "Ошибка сканирования";
      default -> eventCode;
    };
  }

  private static String localizeSeverity(String severity) {
    if (blank(severity)) {
      return "-";
    }
    return switch (severity.trim().toUpperCase(Locale.ROOT)) {
      case "NOT_CLASSIFIED" -> "Не классифицировано";
      case "INFORMATION" -> "Информация";
      case "WARNING" -> "Предупреждение";
      case "AVERAGE" -> "Средний";
      case "HIGH" -> "Высокий";
      case "DISASTER" -> "Катастрофа";
      default -> severity;
    };
  }

  private record MonitoringEventContext(
      Long deviceId,
      String deviceIp,
      String deviceName,
      Set<String> tags,
      String eventCode,
      String severity,
      String metricName,
      String actor,
      String category,
      String action,
      String target,
      String details
  ) {
  }
}
