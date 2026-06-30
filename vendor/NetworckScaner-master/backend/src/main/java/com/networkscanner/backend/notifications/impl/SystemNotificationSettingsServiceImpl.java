package com.networkscanner.backend.notifications.impl;

import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import com.networkscanner.backend.notifications.api.SystemNotificationSettingsService;
import com.networkscanner.backend.notifications.dto.NotificationSubscriptionDto;
import com.networkscanner.backend.notifications.dto.SmtpSettingsDto;
import com.networkscanner.backend.notifications.dto.TestNotificationEventRequest;
import com.networkscanner.backend.notifications.dto.UpdateSmtpSettingsRequest;
import com.networkscanner.backend.notifications.dto.UpsertNotificationSubscriptionRequest;
import com.networkscanner.backend.notifications.model.NotificationSubscriptionEntity;
import com.networkscanner.backend.notifications.model.SmtpSettingsEntity;
import com.networkscanner.backend.notifications.repository.NotificationSubscriptionRepository;
import com.networkscanner.backend.notifications.repository.SmtpSettingsRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SystemNotificationSettingsServiceImpl implements SystemNotificationSettingsService {

  private static final long SMTP_ID = 1L;

  private final SmtpSettingsRepository smtpSettingsRepository;
  private final NotificationSubscriptionRepository subscriptionRepository;
  private final NotificationDispatchService notificationDispatchService;

  public SystemNotificationSettingsServiceImpl(
      SmtpSettingsRepository smtpSettingsRepository,
      NotificationSubscriptionRepository subscriptionRepository,
      NotificationDispatchService notificationDispatchService
  ) {
    this.smtpSettingsRepository = smtpSettingsRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.notificationDispatchService = notificationDispatchService;
  }

  @Override
  @Transactional(readOnly = true)
  public SmtpSettingsDto getSmtpSettings() {
    return toDto(getOrCreateSmtpSettings());
  }

  @Override
  @Transactional
  public SmtpSettingsDto updateSmtpSettings(UpdateSmtpSettingsRequest request) {
    SmtpSettingsEntity entity = getOrCreateSmtpSettings();
    entity.setEnabled(Boolean.TRUE.equals(request.enabled()));
    entity.setServerHost(normalize(request.serverHost(), "localhost"));
    entity.setServerPort(request.serverPort() == null ? 25 : request.serverPort());
    entity.setAuth(Boolean.TRUE.equals(request.auth()));
    entity.setStarttls(Boolean.TRUE.equals(request.starttls()));
    entity.setSsl(Boolean.TRUE.equals(request.ssl()));
    entity.setUsername(normalizeNullable(request.username()));
    if (Boolean.TRUE.equals(request.clearPassword())) {
      entity.setPassword(null);
    } else if (request.password() != null && !request.password().isBlank()) {
      entity.setPassword(request.password());
    }
    entity.setFromEmail(normalize(request.fromEmail(), "no-reply@localhost"));
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return toDto(smtpSettingsRepository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationSubscriptionDto> listSubscriptions(Authentication authentication) {
    String ownerEmail = requireActorEmail(authentication);
    return subscriptionRepository.findByOwnerEmailIgnoreCaseOrderByIdAsc(ownerEmail).stream()
        .map(this::toDto)
        .toList();
  }

  @Override
  @Transactional
  public NotificationSubscriptionDto upsertSubscription(UpsertNotificationSubscriptionRequest request, Authentication authentication) {
    String ownerEmail = requireActorEmail(authentication);
    String actorRoleKind = resolveNotificationKindForActor(authentication);
    String requestedKind = normalize(request.notificationKind(), actorRoleKind).toUpperCase(Locale.ROOT);
    if (!actorRoleKind.equals(requestedKind)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Недопустимый тип подписки для вашей роли: доступен только " + actorRoleKind
      );
    }
    NotificationSubscriptionEntity entity = request.id() == null
        ? new NotificationSubscriptionEntity()
        : subscriptionRepository.findByIdAndOwnerEmailIgnoreCase(request.id(), ownerEmail).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Подписка не найдена."));
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (entity.getId() == null) {
      entity.setCreatedAt(now);
      entity.setOwnerEmail(ownerEmail);
    }
    entity.setEnabled(Boolean.TRUE.equals(request.enabled()));
    entity.setNotificationKind(requestedKind);
    entity.setSubscriptionType(resolveSubscriptionType(request.subscriptionType(), requestedKind));
    entity.setChannel(normalize(request.channel(), "SMTP"));
    entity.setEventCode(joinEventCodes(request.eventCodes()));
    entity.setRecipientEmail(normalizeRecipientEmails(request.recipientEmail()));
    entity.setDeviceIpFilter(normalizeNullable(request.deviceIpFilter()));
    entity.setDeviceTagFilter(normalizeNullable(request.deviceTagFilter()));
    entity.setSeverityFilter(normalizeNullable(request.severityFilter()));
    entity.setMetricFilter(normalizeNullable(request.metricFilter()));
    entity.setCustomCondition(normalizeNullable(request.customCondition()));
    entity.setUpdatedAt(now);
    return toDto(subscriptionRepository.save(entity));
  }

  @Override
  @Transactional
  public void deleteSubscription(long id, Authentication authentication) {
    String ownerEmail = requireActorEmail(authentication);
    if (subscriptionRepository.findByIdAndOwnerEmailIgnoreCase(id, ownerEmail).isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Подписка не найдена.");
    }
    subscriptionRepository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public void triggerTestNotificationEvent(TestNotificationEventRequest request, Authentication authentication) {
    String actorRoleKind = resolveNotificationKindForActor(authentication);
    String requestedKind = normalize(request.notificationKind(), actorRoleKind).toUpperCase(Locale.ROOT);
    if (!actorRoleKind.equals(requestedKind)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недопустимый тип тестового события для вашей роли.");
    }
    String eventCode = normalize(request.eventCode(), "MONITORING_EVENT_OPEN").toUpperCase(Locale.ROOT);
    String actor = requireActorEmail(authentication);
    if ("ADMIN".equals(requestedKind)) {
      notificationDispatchService.notifyAdministrativeEvent(
          actor,
          AuditCategory.AUTH_SESSION,
          AuditAction.UPDATE,
          eventCode,
          normalizeNullable(request.details())
      );
      return;
    }

    if (eventCode.startsWith("SCAN_JOB_")) {
      notificationDispatchService.notifyScanJobEvent(
          1L,
          "TEST_SCAN_JOB",
          eventCode,
          normalizeNullable(request.details())
      );
      return;
    }
    if ("DEVICE_UNMONITORED".equals(eventCode)) {
      notificationDispatchService.notifyAdministrativeEvent(
          actor,
          AuditCategory.MONITORING_DEVICE,
          AuditAction.DELETE,
          "ip=" + normalize(request.deviceIp(), "127.0.0.1"),
          normalizeNullable(request.details())
      );
      return;
    }
    if ("EQUIPMENT_CONFIG_CHANGED".equals(eventCode)) {
      notificationDispatchService.notifyAdministrativeEvent(
          actor,
          AuditCategory.MONITORING_DEVICE,
          AuditAction.UPDATE,
          "ip=" + normalize(request.deviceIp(), "127.0.0.1"),
          "конфиг " + normalize(request.details(), "test")
      );
      return;
    }
    if ("NEW_DEVICE_DISCOVERED".equals(eventCode)) {
      notificationDispatchService.notifyNewDevicesDiscovered(
          1L,
          "TEST_SCAN_JOB",
          List.of(new DeviceScanResult(
              normalize(request.deviceName(), "TEST_DEVICE"),
              normalize(request.deviceName(), "TEST_DEVICE"),
              "TEST-SN",
              normalize(request.deviceIp(), "127.0.0.1"),
              "-",
              "00:11:22:33:44:55",
              "TEST_VENDOR",
              "TEST_MODEL",
              "1.0",
              "ok",
              "ok",
              "test",
              parseTags(request.deviceTags()).stream().toList(),
              List.of(),
              null,
              null
          ))
      );
      return;
    }

    MonitoringEventMutationAction action = "MONITORING_EVENT_RESOLVED".equals(eventCode)
        ? MonitoringEventMutationAction.RESOLVE
        : MonitoringEventMutationAction.OPEN;

    MonitoringEventMutation mutation = new MonitoringEventMutation(
        action,
        normalize(request.metricName(), "test.metric"),
        "test-trigger",
        "Тестовое событие",
        "1=1",
        null,
        null,
        null,
        ThresholdLevel.HIGH,
        0d,
        1d,
        OffsetDateTime.now(ZoneOffset.UTC),
        null,
        normalize(request.severity(), "HIGH")
    );
    Set<String> tags = parseTags(request.deviceTags());
    notificationDispatchService.notifyMonitoringEvent(
        null,
        normalize(request.deviceIp(), "127.0.0.1"),
        normalize(request.deviceName(), "TEST_DEVICE"),
        tags,
        mutation
    );
  }

  private SmtpSettingsEntity getOrCreateSmtpSettings() {
    return smtpSettingsRepository.findById(SMTP_ID).orElseGet(() -> smtpSettingsRepository.save(defaultSmtpSettings()));
  }

  private SmtpSettingsEntity defaultSmtpSettings() {
    SmtpSettingsEntity entity = new SmtpSettingsEntity();
    entity.setId(SMTP_ID);
    entity.setEnabled(false);
    entity.setServerHost("localhost");
    entity.setServerPort(25);
    entity.setAuth(false);
    entity.setStarttls(false);
    entity.setSsl(false);
    entity.setUsername(null);
    entity.setPassword(null);
    entity.setFromEmail("no-reply@localhost");
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return entity;
  }

  private SmtpSettingsDto toDto(SmtpSettingsEntity entity) {
    return new SmtpSettingsDto(
        entity.isEnabled(),
        entity.getServerHost(),
        entity.getServerPort(),
        entity.isAuth(),
        entity.isStarttls(),
        entity.isSsl(),
        entity.getUsername() == null ? "" : entity.getUsername(),
        "",
        entity.getPassword() != null && !entity.getPassword().isBlank(),
        entity.getFromEmail()
    );
  }

  private NotificationSubscriptionDto toDto(NotificationSubscriptionEntity entity) {
    return new NotificationSubscriptionDto(
        entity.getId(),
        entity.isEnabled(),
        entity.getNotificationKind(),
        entity.getSubscriptionType(),
        entity.getChannel(),
        splitEventCodes(entity.getEventCode()),
        entity.getRecipientEmail(),
        entity.getDeviceIpFilter(),
        entity.getDeviceTagFilter(),
        entity.getSeverityFilter(),
        entity.getMetricFilter(),
        entity.getCustomCondition(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  private static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String normalizeNullable(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String joinEventCodes(List<String> eventCodes) {
    if (eventCodes == null || eventCodes.isEmpty()) {
      return "NEW_DEVICE_DISCOVERED";
    }
    String joined = eventCodes.stream()
        .filter(v -> v != null && !v.isBlank())
        .map(String::trim)
        .distinct()
        .collect(Collectors.joining(","));
    return joined.isBlank() ? "NEW_DEVICE_DISCOVERED" : joined;
  }

  private static List<String> splitEventCodes(String stored) {
    if (stored == null || stored.isBlank()) {
      return List.of("NEW_DEVICE_DISCOVERED");
    }
    List<String> items = java.util.Arrays.stream(stored.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .distinct()
        .toList();
    return items.isEmpty() ? List.of("NEW_DEVICE_DISCOVERED") : items;
  }

  private static String normalizeRecipientEmails(String input) {
    if (input == null || input.isBlank()) {
      return "";
    }
    String normalized = java.util.Arrays.stream(input.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .map(v -> v.toLowerCase(Locale.ROOT))
        .distinct()
        .collect(Collectors.joining(","));
    if (normalized.isBlank()) {
      return "";
    }
    for (String email : normalized.split(",")) {
      if (!looksLikeEmail(email)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный email в списке получателей: " + email);
      }
    }
    return normalized;
  }

  private static String resolveSubscriptionType(String requestedType, String notificationKind) {
    if ("ADMIN".equalsIgnoreCase(notificationKind)) {
      return "SYSTEM";
    }
    String normalized = normalize(requestedType, "DEVICE").toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "DEVICE", "TAG_GROUP", "SCAN_JOB" -> normalized;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный тип подписки: " + normalized);
    };
  }

  private static Set<String> parseTags(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private static boolean looksLikeEmail(String value) {
    int at = value.indexOf('@');
    int lastDot = value.lastIndexOf('.');
    return at > 0 && lastDot > at + 1 && lastDot < value.length() - 1;
  }

  private static String requireActorEmail(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось определить пользователя.");
    }
    return authentication.getName().trim().toLowerCase(Locale.ROOT);
  }

  private static String resolveNotificationKindForActor(Authentication authentication) {
    if (authentication == null || authentication.getAuthorities() == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав.");
    }
    boolean admin = authentication.getAuthorities().stream()
        .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
    boolean operator = authentication.getAuthorities().stream()
        .anyMatch(a -> "ROLE_OPERATOR".equalsIgnoreCase(a.getAuthority()));
    if (admin) {
      return "ADMIN";
    }
    if (operator) {
      return "OPERATOR";
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Подписки доступны только ролям ADMIN и OPERATOR.");
  }
}
