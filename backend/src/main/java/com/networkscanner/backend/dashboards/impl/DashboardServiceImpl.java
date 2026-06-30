package com.networkscanner.backend.dashboards.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.dashboards.dto.DashboardCreateRequest;
import com.networkscanner.backend.dashboards.dto.DashboardDto;
import com.networkscanner.backend.dashboards.dto.DashboardUpdateRequest;
import com.networkscanner.backend.dashboards.dto.WidgetDto;
import com.networkscanner.backend.dashboards.dto.WidgetFieldDto;
import com.networkscanner.backend.dashboards.dto.WidgetPageDto;
import com.networkscanner.backend.dashboards.model.AbstractWidgetEntity;
import com.networkscanner.backend.dashboards.model.ClockWidgetEntity;
import com.networkscanner.backend.dashboards.model.DashboardEntity;
import com.networkscanner.backend.dashboards.model.DashboardWidgetFieldEntity;
import com.networkscanner.backend.dashboards.model.DashboardVisibility;
import com.networkscanner.backend.dashboards.model.GraphWidgetEntity;
import com.networkscanner.backend.dashboards.model.PlaceholderWidgetEntity;
import com.networkscanner.backend.dashboards.model.ProblemsWidgetEntity;
import com.networkscanner.backend.dashboards.repository.AbstractWidgetRepository;
import com.networkscanner.backend.dashboards.repository.DashboardRepository;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardServiceImpl implements DashboardService {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private final DashboardRepository dashboardRepository;
  private final AbstractWidgetRepository widgetRepository;
  private final AppUserRepository appUserRepository;
  private final AuditLogService auditLogService;

  public DashboardServiceImpl(
      DashboardRepository dashboardRepository,
      AbstractWidgetRepository widgetRepository,
      AppUserRepository appUserRepository,
      AuditLogService auditLogService
  ) {
    this.dashboardRepository = dashboardRepository;
    this.widgetRepository = widgetRepository;
    this.appUserRepository = appUserRepository;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DashboardDto> listAccessible(Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    List<DashboardEntity> entities;
    if (admin) {
      entities = dashboardRepository.findAll(Sort.by(Order.desc("updatedAt")));
    } else {
      entities = dashboardRepository.findAllAccessibleByUserId(actor.getId());
    }
    return entities.stream()
        .sorted(Comparator.comparing(DashboardEntity::getUpdatedAt).reversed())
        .map(this::toDtoWithoutWidgets)
        .toList();
  }

  @Override
  @Transactional
  public DashboardDto create(DashboardCreateRequest request, Authentication authentication) {
    AppUser owner = requireCurrentUser(authentication);
    Set<Long> shared = normalizeSharedUserIds(request.sharedUserIds(), owner.getId());
    applyVisibilityToShared(request.visibility(), shared);
    validateUserIdsExist(shared);

    DashboardEntity entity = new DashboardEntity();
    entity.setOwner(owner);
    entity.setName(request.name().strip());
    entity.setVisibility(request.visibility());
    entity.setSharedUserIds(shared);

    DashboardEntity saved = dashboardRepository.save(entity);
    auditLogService.record(
        authentication,
        AuditCategory.DASHBOARD,
        AuditAction.CREATE,
        "id=" + saved.getId() + ", name=" + saved.getName(),
        null
    );
    return toDtoWithoutWidgets(saved);
  }

  @Override
  @Transactional
  public DashboardDto update(Long id, DashboardUpdateRequest request, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    DashboardEntity entity = dashboardRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Дашборд не найден."));
    if (!canModify(entity, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Изменять дашборд может только владелец или администратор.");
    }

    Set<Long> shared = normalizeSharedUserIds(request.sharedUserIds(), entity.getOwner().getId());
    applyVisibilityToShared(request.visibility(), shared);
    validateUserIdsExist(shared);

    entity.setName(request.name().strip());
    entity.setVisibility(request.visibility());
    entity.setSharedUserIds(shared);
    auditLogService.record(
        authentication,
        AuditCategory.DASHBOARD,
        AuditAction.UPDATE,
        "id=" + id + ", name=" + entity.getName(),
        null
    );
    return toDtoWithoutWidgets(entity);
  }

  @Override
  @Transactional
  public void delete(Long id, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    DashboardEntity entity = dashboardRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Дашборд не найден."));
    if (!canModify(entity, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Удалять дашборд может только владелец или администратор.");
    }
    String dashName = entity.getName();
    dashboardRepository.delete(entity);
    auditLogService.record(
        authentication,
        AuditCategory.DASHBOARD,
        AuditAction.DELETE,
        "id=" + id + ", name=" + dashName,
        null
    );
  }

  @Override
  @Transactional(readOnly = true)
  public DashboardDto getById(Long id, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    DashboardEntity entity = dashboardRepository.findFetchedById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Дашборд не найден."));
    if (!canRead(entity, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому дашборду.");
    }
    return toDtoWithWidgets(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isReadableByUser(Long dashboardId, Long userId, boolean admin) {
    if (dashboardId == null || userId == null) {
      return false;
    }
    DashboardEntity entity = dashboardRepository.findById(dashboardId).orElse(null);
    if (entity == null) {
      return false;
    }
    return canRead(entity, userId, admin);
  }

  @Override
  @Transactional(readOnly = true)
  public WidgetPageDto listWidgets(
      String search,
      Long dashboardId,
      String widgetType,
      int page,
      int size,
      Authentication authentication
  ) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    if (dashboardId != null) {
      DashboardEntity d = dashboardRepository.findById(dashboardId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Дашборд не найден."));
      if (!canRead(d, actor.getId(), admin)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому дашборду.");
      }
    }

    int p = Math.max(0, page);
    int sz = Math.min(Math.max(1, size == 0 ? DEFAULT_PAGE_SIZE : size), MAX_PAGE_SIZE);
    var pageable = PageRequest.of(p, sz, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));

    Specification<AbstractWidgetEntity> spec = WidgetSpecifications.accessible(actor.getId(), admin)
        .and(WidgetSpecifications.nameContains(search))
        .and(WidgetSpecifications.dashboardIdEquals(dashboardId))
        .and(WidgetSpecifications.widgetTypeEquals(widgetType));

    Page<AbstractWidgetEntity> result = widgetRepository.findAll(spec, pageable);
    List<WidgetDto> content = result.getContent().stream().map(this::toWidgetDto).collect(Collectors.toList());
    return new WidgetPageDto(
        content,
        result.getTotalElements(),
        result.getTotalPages(),
        result.getNumber(),
        result.getSize(),
        result.isFirst(),
        result.isLast()
    );
  }

  private DashboardDto toDtoWithoutWidgets(DashboardEntity e) {
    return new DashboardDto(
        e.getId(),
        e.getOwnerId(),
        e.getName(),
        e.getVisibility(),
        new LinkedHashSet<>(e.getSharedUserIds()),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        List.of()
    );
  }

  private DashboardDto toDtoWithWidgets(DashboardEntity e) {
    List<WidgetDto> widgets = e.getWidgets().stream().map(this::toWidgetDto).collect(Collectors.toList());
    return new DashboardDto(
        e.getId(),
        e.getOwnerId(),
        e.getName(),
        e.getVisibility(),
        new LinkedHashSet<>(e.getSharedUserIds()),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        widgets
    );
  }

  private WidgetDto toWidgetDto(AbstractWidgetEntity w) {
    List<WidgetFieldDto> fields = w.getFields().stream()
        .map(this::toFieldDto)
        .collect(Collectors.toList());
    return new WidgetDto(
        w.getId(),
        w.getDashboard().getId(),
        w.getSortOrder(),
        w.getName(),
        widgetTypeName(w),
        w.getGridX(),
        w.getGridY(),
        w.getWidth(),
        w.getHeight(),
        w.getViewMode(),
        w.getRefreshIntervalSeconds(),
        w.isShowHeader(),
        w.getBorderWidthPx(),
        w.getBorderColor(),
        fields
    );
  }

  private WidgetFieldDto toFieldDto(DashboardWidgetFieldEntity field) {
    return new WidgetFieldDto(field.getId(), field.getName(), field.getValueInt(), field.getValueStr());
  }

  private static String widgetTypeName(AbstractWidgetEntity w) {
    if (w instanceof ClockWidgetEntity) {
      return "CLOCK";
    }
    if (w instanceof ProblemsWidgetEntity) {
      return "PROBLEMS";
    }
    if (w instanceof GraphWidgetEntity) {
      return "GRAPH";
    }
    if (w instanceof PlaceholderWidgetEntity) {
      return "PLACEHOLDER";
    }
    return "UNKNOWN";
  }

  private static boolean canRead(DashboardEntity d, Long userId, boolean admin) {
    if (admin) {
      return true;
    }
    if (d.getOwner().getId().equals(userId)) {
      return true;
    }
    return d.getSharedUserIds().contains(userId);
  }

  private static boolean canModify(DashboardEntity d, Long userId, boolean admin) {
    return admin || d.getOwner().getId().equals(userId);
  }

  private static void applyVisibilityToShared(DashboardVisibility visibility, Set<Long> shared) {
    if (visibility == DashboardVisibility.PRIVATE) {
      shared.clear();
    }
  }

  private static Set<Long> normalizeSharedUserIds(Set<Long> raw, Long ownerId) {
    Set<Long> out = new LinkedHashSet<>(raw);
    out.remove(ownerId);
    return out;
  }

  private void validateUserIdsExist(Set<Long> userIds) {
    for (Long uid : userIds) {
      if (!appUserRepository.existsById(uid)) {
        throw new IllegalArgumentException("Пользователь с id " + uid + " не найден.");
      }
    }
  }

  private AppUser requireCurrentUser(Authentication authentication) {
    String email = authentication.getName();
    return appUserRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден."));
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
