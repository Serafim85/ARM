package com.networkscanner.backend.audit.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.dto.AuditEventDto;
import com.networkscanner.backend.audit.dto.AuditEventListQuery;
import com.networkscanner.backend.audit.dto.AuditEventPageDto;
import com.networkscanner.backend.audit.model.AppAuditEventEntity;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.audit.repository.AppAuditEventRepository;
import com.networkscanner.backend.audit.repository.AppAuditEventSpecifications;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {

  private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

  private final AppAuditEventRepository repository;
  private final NotificationDispatchService notificationDispatchService;

  public AuditLogServiceImpl(AppAuditEventRepository repository, NotificationDispatchService notificationDispatchService) {
    this.repository = repository;
    this.notificationDispatchService = notificationDispatchService;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      Authentication authentication,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  ) {
    String actor = authentication != null ? authentication.getName() : "system";
    persist(actor, category, action, target, details);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordForActor(
      String actorLogin,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  ) {
    String actor = actorLogin != null && !actorLogin.isBlank() ? actorLogin : "system";
    persist(actor, category, action, target, details);
  }

  private void persist(
      String actorLogin,
      AuditCategory category,
      AuditAction action,
      String target,
      String details
  ) {
    if (target == null || target.isBlank()) {
      target = "(без имени)";
    }
    String persistedTarget = truncate(target, 512);
    String persistedDetails = details == null || details.isBlank() ? null : details;
    try {
      AppAuditEventEntity entity = new AppAuditEventEntity();
      entity.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
      entity.setActorLogin(actorLogin);
      entity.setCategory(category);
      entity.setAction(action);
      entity.setTarget(persistedTarget);
      entity.setDetails(persistedDetails);
      repository.save(entity);
    } catch (RuntimeException e) {
      log.warn("Не удалось записать событие аудита: {}", e.getMessage());
      return;
    }

    // Уведомления отправляем в фоне, чтобы не блокировать критичные операции (например, login).
    CompletableFuture.runAsync(() -> {
      try {
        notificationDispatchService.notifyAdministrativeEvent(actorLogin, category, action, persistedTarget, persistedDetails);
      } catch (RuntimeException e) {
        log.warn("Не удалось отправить административное уведомление: {}", e.getMessage());
      }
    });
  }

  @Override
  @Transactional(readOnly = true)
  public AuditEventPageDto list(Pageable pageable, AuditEventListQuery query) {
    Page<AppAuditEventEntity> page = repository.findAll(AppAuditEventSpecifications.matching(query), pageable);
    List<AuditEventDto> content = page.getContent().stream()
        .map(e -> new AuditEventDto(
            e.getOccurredAt(),
            e.getActorLogin(),
            e.getCategory(),
            e.getAction(),
            e.getTarget(),
            e.getDetails()
        ))
        .toList();
    return new AuditEventPageDto(
        content,
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize(),
        page.isFirst(),
        page.isLast()
    );
  }

  private static String truncate(String s, int max) {
    if (s.length() <= max) {
      return s;
    }
    return s.substring(0, max - 1) + "…";
  }
}
