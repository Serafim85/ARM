package com.networkscanner.backend.notifications.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import com.networkscanner.backend.notifications.api.SystemNotificationSettingsService;
import com.networkscanner.backend.notifications.dto.SmtpSettingsDto;
import com.networkscanner.backend.notifications.dto.TestSmtpRequest;
import com.networkscanner.backend.notifications.dto.UpdateSmtpSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/smtp-settings")
@Tag(name = "SMTP настройки", description = "Настройка почтового сервера и отправки уведомлений (только ADMIN)")
public class AdminSmtpSettingsController {

  private static final Logger log = LoggerFactory.getLogger(AdminSmtpSettingsController.class);

  private final SystemNotificationSettingsService service;
  private final NotificationDispatchService notificationDispatchService;
  private final AuditLogService auditLogService;

  public AdminSmtpSettingsController(
      SystemNotificationSettingsService service,
      NotificationDispatchService notificationDispatchService,
      AuditLogService auditLogService
  ) {
    this.service = service;
    this.notificationDispatchService = notificationDispatchService;
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "Текущие SMTP-настройки")
  public SmtpSettingsDto getSettings() {
    return service.getSmtpSettings();
  }

  @PutMapping
  @Operation(summary = "Сохранить SMTP-настройки")
  public SmtpSettingsDto updateSettings(
      @Valid @RequestBody UpdateSmtpSettingsRequest request,
      Authentication authentication
  ) {
    SmtpSettingsDto settings = service.updateSmtpSettings(request);
    auditLogService.record(
        authentication,
        AuditCategory.NOTIFICATION_SETTINGS,
        AuditAction.UPDATE,
        "smtp-settings",
        "host=" + settings.serverHost() + ":" + settings.serverPort() + "; enabled=" + settings.enabled()
    );
    return settings;
  }

  @PutMapping("/test")
  @Operation(summary = "Отправить тестовое SMTP-письмо")
  public void sendTest(@RequestBody(required = false) TestSmtpRequest request) {
    String recipient = request == null ? null : request.recipientEmail();
    log.info("Запрос тестового SMTP-письма получен. recipient={}", recipient == null ? "(null)" : recipient);
    notificationDispatchService.sendTestEmail(recipient, request == null ? null : request.smtpSettings());
    log.info("Запрос тестового SMTP-письма успешно обработан. recipient={}", recipient == null ? "(null)" : recipient);
  }
}
