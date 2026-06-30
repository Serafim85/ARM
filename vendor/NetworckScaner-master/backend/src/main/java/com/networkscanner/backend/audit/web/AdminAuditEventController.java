package com.networkscanner.backend.audit.web;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.dto.AuditEventListQuery;
import com.networkscanner.backend.audit.dto.AuditEventPageDto;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Аудит", description = "Журнал действий пользователей в системе (только роль ADMIN)")
public class AdminAuditEventController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private final AuditLogService auditLogService;

  public AdminAuditEventController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping("/events")
  @Operation(
      summary = "Страница журнала аудита",
      description = "События по убыванию времени. Необязательные фильтры: интервал по времени, подстрока в логине пользователя, раздел и действие."
  )
  public AuditEventPageDto listEvents(
      @Parameter(description = "Номер страницы (с нуля)")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Размер страницы")
      @RequestParam(defaultValue = "0") int size,
      @Parameter(description = "Начало интервала времени (ISO-8601)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @Parameter(description = "Конец интервала времени (ISO-8601)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @Parameter(description = "Поиск по логину пользователя (подстрока, без учёта регистра)")
      @RequestParam(required = false) String actor,
      @Parameter(description = "Раздел (категория)")
      @RequestParam(required = false) AuditCategory category,
      @Parameter(description = "Действие")
      @RequestParam(required = false) AuditAction action
  ) {
    int p = Math.max(0, page);
    int sz = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(p, sz, Sort.by(Sort.Direction.DESC, "occurredAt"));
    AuditEventListQuery query = new AuditEventListQuery(from, to, actor, category, action);
    return auditLogService.list(pageable, query);
  }
}
