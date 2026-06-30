package com.networkscanner.backend.notifications.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.notifications.api.SystemNotificationSettingsService;
import com.networkscanner.backend.notifications.dto.NotificationSubscriptionDto;
import com.networkscanner.backend.notifications.dto.TestNotificationEventRequest;
import com.networkscanner.backend.notifications.dto.UpsertNotificationSubscriptionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/notification-subscriptions")
@Tag(name = "Подписки уведомлений", description = "Управление SMTP-подписками операторских и административных уведомлений")
public class AdminNotificationSubscriptionController {

  private final SystemNotificationSettingsService service;
  private final AuditLogService auditLogService;

  public AdminNotificationSubscriptionController(
      SystemNotificationSettingsService service,
      AuditLogService auditLogService
  ) {
    this.service = service;
    this.auditLogService = auditLogService;
  }

  @GetMapping
  @Operation(summary = "Список подписок")
  public List<NotificationSubscriptionDto> list(Authentication authentication) {
    return service.listSubscriptions(authentication);
  }

  @PostMapping
  @Operation(summary = "Создать или обновить подписку")
  public NotificationSubscriptionDto upsert(
      @Valid @RequestBody UpsertNotificationSubscriptionRequest request,
      Authentication authentication
  ) {
    boolean isCreate = request.id() == null;
    NotificationSubscriptionDto subscription = service.upsertSubscription(request, authentication);
    auditLogService.record(
        authentication,
        AuditCategory.NOTIFICATION_SETTINGS,
        isCreate ? AuditAction.CREATE : AuditAction.UPDATE,
        "subscriptionId=" + subscription.id(),
        "recipient=" + subscription.recipientEmail() + "; events=" + subscription.eventCodes()
    );
    return subscription;
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Удалить подписку")
  public void delete(@PathVariable long id, Authentication authentication) {
    service.deleteSubscription(id, authentication);
    auditLogService.record(
        authentication,
        AuditCategory.NOTIFICATION_SETTINGS,
        AuditAction.DELETE,
        "subscriptionId=" + id,
        "Подписка на уведомления удалена."
    );
  }

  @PostMapping("/test-event")
  @Operation(summary = "Отправить тестовое событие для проверки подписок")
  public void testEvent(@Valid @RequestBody TestNotificationEventRequest request, Authentication authentication) {
    service.triggerTestNotificationEvent(request, authentication);
  }
}
