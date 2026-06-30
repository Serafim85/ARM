package com.networkscanner.backend.notifications.api;

import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.notifications.dto.SmtpTestDraftRequest;
import java.util.Collection;
import java.util.List;

public interface NotificationDispatchService {
  void sendTestEmail(String recipientEmail, SmtpTestDraftRequest smtpDraft);
  void notifyNewDevicesDiscovered(long scanJobId, String scanJobName, List<DeviceScanResult> devices);
  void notifyAdministrativeEvent(
      String actorLogin,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  );

  void notifyMonitoringEvent(
      Long deviceId,
      String deviceIp,
      String deviceName,
      Collection<String> deviceTags,
      MonitoringEventMutation mutation
  );

  void notifyScanJobEvent(
      long scanJobId,
      String scanJobName,
      String eventCode,
      String details
  );
}
