package com.networkscanner.backend.notifications.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import com.networkscanner.backend.notifications.dto.TestNotificationEventRequest;
import com.networkscanner.backend.notifications.repository.NotificationSubscriptionRepository;
import com.networkscanner.backend.notifications.repository.SmtpSettingsRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class SystemNotificationSettingsServiceImplTest {

  private final SmtpSettingsRepository smtpSettingsRepository = Mockito.mock(SmtpSettingsRepository.class);
  private final NotificationSubscriptionRepository subscriptionRepository = Mockito.mock(NotificationSubscriptionRepository.class);
  private final NotificationDispatchService notificationDispatchService = Mockito.mock(NotificationDispatchService.class);

  private final SystemNotificationSettingsServiceImpl service = new SystemNotificationSettingsServiceImpl(
      smtpSettingsRepository,
      subscriptionRepository,
      notificationDispatchService
  );

  @Test
  void triggerTestEvent_operatorMonitoringOpen_callsMonitoringDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "MONITORING_EVENT_OPEN",
            "10.0.0.10",
            "sw-core-1",
            "HIGH",
            "cpu.util",
            "core,datacenter",
            "test"
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    );

    verify(notificationDispatchService).notifyMonitoringEvent(
        eq(null),
        eq("10.0.0.10"),
        eq("sw-core-1"),
        eq(java.util.Set.of("core", "datacenter")),
        any()
    );
    verify(notificationDispatchService, never()).notifyScanJobEvent(any(Long.class), any(), any(), any());
  }

  @Test
  void triggerTestEvent_operatorMonitoringResolved_callsMonitoringDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "MONITORING_EVENT_RESOLVED",
            "10.0.0.11",
            "sw-core-2",
            "WARNING",
            "memory.util",
            "core",
            "resolved test"
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    );

    verify(notificationDispatchService).notifyMonitoringEvent(
        eq(null),
        eq("10.0.0.11"),
        eq("sw-core-2"),
        eq(java.util.Set.of("core")),
        any()
    );
  }

  @Test
  void triggerTestEvent_operatorNewDeviceDiscovered_callsNewDeviceDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "NEW_DEVICE_DISCOVERED",
            "10.0.0.12",
            "new-switch",
            null,
            null,
            "edge",
            "new device test"
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    );

    verify(notificationDispatchService).notifyNewDevicesDiscovered(eq(1L), eq("TEST_SCAN_JOB"), any());
    verify(notificationDispatchService, never()).notifyMonitoringEvent(any(), any(), any(), any(), any());
  }

  @Test
  void triggerTestEvent_operatorScanJob_callsScanDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "SCAN_JOB_COMPLETED",
            null,
            null,
            null,
            null,
            null,
            "scan done"
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    );

    verify(notificationDispatchService).notifyScanJobEvent(1L, "TEST_SCAN_JOB", "SCAN_JOB_COMPLETED", "scan done");
    verify(notificationDispatchService, never()).notifyMonitoringEvent(any(), any(), any(), any(), any());
  }

  @Test
  void triggerTestEvent_operatorDeviceUnmonitored_callsAdministrativeDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "DEVICE_UNMONITORED",
            "10.0.0.20",
            null,
            null,
            null,
            null,
            "manual remove"
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    );

    verify(notificationDispatchService).notifyAdministrativeEvent(
        "operator@example.com",
        AuditCategory.MONITORING_DEVICE,
        AuditAction.DELETE,
        "ip=10.0.0.20",
        "manual remove"
    );
  }

  @Test
  void triggerTestEvent_operatorEquipmentConfigChanged_callsAdministrativeDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "EQUIPMENT_CONFIG_CHANGED",
            "10.0.0.30",
            null,
            null,
            null,
            null,
            "config drift"
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    );

    verify(notificationDispatchService).notifyAdministrativeEvent(
        "operator@example.com",
        AuditCategory.MONITORING_DEVICE,
        AuditAction.UPDATE,
        "ip=10.0.0.30",
        "конфиг config drift"
    );
  }

  @Test
  void triggerTestEvent_adminEvent_callsAdministrativeDispatch() {
    service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "ADMIN",
            "USER_CREATED",
            null,
            null,
            null,
            null,
            null,
            "created by test"
        ),
        auth("admin@example.com", "ROLE_ADMIN")
    );

    verify(notificationDispatchService).notifyAdministrativeEvent(
        "admin@example.com",
        AuditCategory.AUTH_SESSION,
        AuditAction.UPDATE,
        "USER_CREATED",
        "created by test"
    );
  }

  @Test
  void triggerTestEvent_operatorCannotSendAdminKind_throwsForbidden() {
    assertThatThrownBy(() -> service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "ADMIN",
            "USER_CREATED",
            null,
            null,
            null,
            null,
            null,
            null
        ),
        auth("operator@example.com", "ROLE_OPERATOR")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");

    verifyNoInteractions(notificationDispatchService);
  }

  @Test
  void triggerTestEvent_adminCannotSendOperatorKind_throwsForbidden() {
    assertThatThrownBy(() -> service.triggerTestNotificationEvent(
        new TestNotificationEventRequest(
            "OPERATOR",
            "MONITORING_EVENT_OPEN",
            null,
            null,
            null,
            null,
            null,
            null
        ),
        auth("admin@example.com", "ROLE_ADMIN")
    ))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");

    verifyNoInteractions(notificationDispatchService);
  }

  private static Authentication auth(String email, String role) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "N/A",
        List.of(new SimpleGrantedAuthority(role))
    );
  }
}
