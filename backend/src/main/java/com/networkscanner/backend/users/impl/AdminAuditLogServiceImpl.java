package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.AdminAuditLogService;
import com.networkscanner.backend.users.dto.AdminAuditLogDto;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** @deprecated См. {@link AdminAuditLogService}. */
@Deprecated
@Service
public class AdminAuditLogServiceImpl implements AdminAuditLogService {

  private static final int MAX_ENTRIES = 100;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final Deque<AdminAuditLogDto> entries = new ConcurrentLinkedDeque<>();
  private final NotificationDispatchService notificationDispatchService;

  public AdminAuditLogServiceImpl(NotificationDispatchService notificationDispatchService) {
    this.notificationDispatchService = notificationDispatchService;
  }

  @Override
  public List<AdminAuditLogDto> list() {
    return new ArrayList<>(entries);
  }

  @Override
  public void record(Authentication authentication, String action, String target, String details) {
    String actor = authentication != null ? authentication.getName() : "system";
    entries.addFirst(new AdminAuditLogDto(
        OffsetDateTime.now().format(DATE_TIME_FORMATTER),
        actor,
        action,
        target,
        details
    ));

    while (entries.size() > MAX_ENTRIES) {
      entries.removeLast();
    }
    CompletableFuture.runAsync(() -> {
      try {
        notificationDispatchService.notifyAdministrativeEvent(actor, null, null, action, details);
      } catch (RuntimeException ignored) {
        // Ошибка уведомления не должна ломать пользовательские административные операции.
      }
    });
  }
}
