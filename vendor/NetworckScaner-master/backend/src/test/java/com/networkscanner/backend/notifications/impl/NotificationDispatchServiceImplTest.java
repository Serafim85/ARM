package com.networkscanner.backend.notifications.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.notifications.model.NotificationSubscriptionEntity;
import com.networkscanner.backend.notifications.model.SmtpSettingsEntity;
import com.networkscanner.backend.notifications.repository.NotificationSubscriptionRepository;
import com.networkscanner.backend.notifications.repository.SmtpSettingsRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class NotificationDispatchServiceImplTest {

  private final NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
  private final SmtpSettingsRepository smtpSettingsRepository = mock(SmtpSettingsRepository.class);
  private final MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);

  private final NotificationDispatchServiceImpl service = new NotificationDispatchServiceImpl(
      subscriptionRepository,
      smtpSettingsRepository,
      monitoredDeviceRepository,
      new ObjectMapper()
  );

  @Test
  void notifyMonitoringEvent_customConditionAnd_matchesAndSends() {
    NotificationSubscriptionEntity subscription = operatorSubscription(
        "MONITORING_EVENT_OPEN",
        "HIGH",
        "cpu",
        "event=MONITORING_EVENT_OPEN && severity=HIGH && metric=cpu && tag=core"
    );
    when(subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase("SMTP")).thenReturn(List.of(subscription));
    when(smtpSettingsRepository.findById(1L)).thenReturn(Optional.of(readySmtp()));

    try (MockedConstruction<JavaMailSenderImpl> ignored = Mockito.mockConstruction(
        JavaMailSenderImpl.class,
        (mock, context) -> {
          when(mock.getJavaMailProperties()).thenReturn(new Properties());
          when(mock.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        }
    )) {
      service.notifyMonitoringEvent(
          10L,
          "10.10.10.10",
          "sw-core-1",
          Set.of("core", "dc"),
          mutation(MonitoringEventMutationAction.OPEN, "cpu.util", "HIGH")
      );

      verify(ignored.constructed().get(0)).send(any(MimeMessage.class));
    }
  }

  @Test
  void notifyMonitoringEvent_customConditionNegation_blocksSending() {
    NotificationSubscriptionEntity subscription = operatorSubscription(
        "MONITORING_EVENT_OPEN",
        "HIGH",
        "cpu",
        "event=MONITORING_EVENT_OPEN && !tag=core"
    );
    when(subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase("SMTP")).thenReturn(List.of(subscription));
    when(smtpSettingsRepository.findById(1L)).thenReturn(Optional.of(readySmtp()));

    try (MockedConstruction<JavaMailSenderImpl> ignored = Mockito.mockConstruction(
        JavaMailSenderImpl.class,
        (mock, context) -> {
          when(mock.getJavaMailProperties()).thenReturn(new Properties());
          when(mock.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        }
    )) {
      service.notifyMonitoringEvent(
          10L,
          "10.10.10.10",
          "sw-core-1",
          Set.of("core", "dc"),
          mutation(MonitoringEventMutationAction.OPEN, "cpu.util", "HIGH")
      );

      assertThat(ignored.constructed()).isEmpty();
    }
  }

  @Test
  void notifyAdministrativeEvent_customConditionOnActor_matchesAndSends() {
    NotificationSubscriptionEntity subscription = new NotificationSubscriptionEntity();
    subscription.setEnabled(true);
    subscription.setNotificationKind("ADMIN");
    subscription.setSubscriptionType("SYSTEM");
    subscription.setChannel("SMTP");
    subscription.setEventCode("USER_LOGIN_FAILED");
    subscription.setRecipientEmail("admin@example.com");
    subscription.setCustomCondition("event=USER_LOGIN_FAILED && actor=admin@example.com");
    subscription.setOwnerEmail("admin@example.com");
    subscription.setCreatedAt(OffsetDateTime.now());
    subscription.setUpdatedAt(OffsetDateTime.now());
    when(subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase("SMTP")).thenReturn(List.of(subscription));
    when(smtpSettingsRepository.findById(1L)).thenReturn(Optional.of(readySmtp()));
    when(monitoredDeviceRepository.findByIp(any())).thenReturn(Optional.empty());

    try (MockedConstruction<JavaMailSenderImpl> ignored = Mockito.mockConstruction(
        JavaMailSenderImpl.class,
        (mock, context) -> {
          when(mock.getJavaMailProperties()).thenReturn(new Properties());
          when(mock.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        }
    )) {
      service.notifyAdministrativeEvent(
          "admin@example.com",
          AuditCategory.AUTH_SESSION,
          AuditAction.LOGIN_FAILED,
          "auth",
          "bad creds"
      );

      verify(ignored.constructed().get(0)).send(any(MimeMessage.class));
    }
  }

  @Test
  void notifyAdministrativeEvent_operatorDeviceUnmonitored_ignoresSeverityMetricFilters() {
    NotificationSubscriptionEntity subscription = new NotificationSubscriptionEntity();
    subscription.setEnabled(true);
    subscription.setNotificationKind("OPERATOR");
    subscription.setSubscriptionType("DEVICE");
    subscription.setChannel("SMTP");
    subscription.setEventCode("DEVICE_UNMONITORED");
    subscription.setRecipientEmail("ops@example.com");
    subscription.setOwnerEmail("ops@example.com");
    subscription.setSeverityFilter("DISASTER");
    subscription.setMetricFilter("mem");
    subscription.setCreatedAt(OffsetDateTime.now());
    subscription.setUpdatedAt(OffsetDateTime.now());
    when(subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase("SMTP")).thenReturn(List.of(subscription));
    when(smtpSettingsRepository.findById(1L)).thenReturn(Optional.of(readySmtp()));
    when(monitoredDeviceRepository.findByIp("10.1.1.1")).thenReturn(Optional.of(deviceWithTags("10.1.1.1", "[\"core\"]")));

    try (MockedConstruction<JavaMailSenderImpl> ignored = Mockito.mockConstruction(
        JavaMailSenderImpl.class,
        (mock, context) -> {
          when(mock.getJavaMailProperties()).thenReturn(new Properties());
          when(mock.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        }
    )) {
      service.notifyAdministrativeEvent(
          "operator@example.com",
          AuditCategory.MONITORING_DEVICE,
          AuditAction.DELETE,
          "ip=10.1.1.1",
          "removed"
      );

      verify(ignored.constructed().get(0)).send(any(MimeMessage.class));
    }
  }

  @Test
  void notifyMonitoringEvent_resolvedEventMatchesResolvedSubscription() {
    NotificationSubscriptionEntity subscription = operatorSubscription(
        "MONITORING_EVENT_RESOLVED",
        null,
        null,
        "event=MONITORING_EVENT_RESOLVED && severity=HIGH"
    );
    when(subscriptionRepository.findByEnabledTrueAndChannelIgnoreCase("SMTP")).thenReturn(List.of(subscription));
    when(smtpSettingsRepository.findById(1L)).thenReturn(Optional.of(readySmtp()));

    try (MockedConstruction<JavaMailSenderImpl> ignored = Mockito.mockConstruction(
        JavaMailSenderImpl.class,
        (mock, context) -> {
          when(mock.getJavaMailProperties()).thenReturn(new Properties());
          when(mock.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        }
    )) {
      service.notifyMonitoringEvent(
          11L,
          "10.10.10.11",
          "sw-core-2",
          Set.of("core"),
          mutation(MonitoringEventMutationAction.RESOLVE, "cpu.util", "HIGH")
      );

      verify(ignored.constructed().get(0)).send(any(MimeMessage.class));
    }
  }

  private static NotificationSubscriptionEntity operatorSubscription(
      String events,
      String severityFilter,
      String metricFilter,
      String customCondition
  ) {
    NotificationSubscriptionEntity subscription = new NotificationSubscriptionEntity();
    subscription.setEnabled(true);
    subscription.setNotificationKind("OPERATOR");
    subscription.setSubscriptionType("DEVICE");
    subscription.setChannel("SMTP");
    subscription.setEventCode(events);
    subscription.setRecipientEmail("ops@example.com");
    subscription.setOwnerEmail("ops@example.com");
    subscription.setDeviceIpFilter("10.10.10.10,10.1.1.1,10.10.10.11");
    subscription.setDeviceTagFilter("core");
    subscription.setSeverityFilter(severityFilter);
    subscription.setMetricFilter(metricFilter);
    subscription.setCustomCondition(customCondition);
    subscription.setCreatedAt(OffsetDateTime.now());
    subscription.setUpdatedAt(OffsetDateTime.now());
    return subscription;
  }

  private static MonitoringEventMutation mutation(
      MonitoringEventMutationAction action,
      String metric,
      String severity
  ) {
    return new MonitoringEventMutation(
        action,
        metric,
        "trigger-1",
        "CPU trigger",
        "expr",
        null,
        null,
        null,
        ThresholdLevel.HIGH,
        80d,
        95d,
        OffsetDateTime.now(),
        action == MonitoringEventMutationAction.RESOLVE ? OffsetDateTime.now() : null,
        severity
    );
  }

  private static SmtpSettingsEntity readySmtp() {
    SmtpSettingsEntity smtp = new SmtpSettingsEntity();
    smtp.setId(1L);
    smtp.setEnabled(true);
    smtp.setServerHost("smtp.example.local");
    smtp.setServerPort(25);
    smtp.setAuth(false);
    smtp.setStarttls(false);
    smtp.setSsl(false);
    smtp.setFromEmail("no-reply@example.com");
    smtp.setUpdatedAt(OffsetDateTime.now());
    return smtp;
  }

  private static MonitoredDeviceEntity deviceWithTags(String ip, String tagsJson) {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(5L);
    device.setIp(ip);
    device.setName("sw-core");
    device.setTagsJson(tagsJson);
    return device;
  }
}
