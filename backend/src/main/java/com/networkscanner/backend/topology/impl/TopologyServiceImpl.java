package com.networkscanner.backend.topology.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.topology.dto.TopologyCreateRequest;
import com.networkscanner.backend.topology.dto.TopologyDto;
import com.networkscanner.backend.topology.dto.TopologyUpdateRequest;
import com.networkscanner.backend.topology.model.TopologyEntity;
import com.networkscanner.backend.topology.model.TopologyVisibility;
import com.networkscanner.backend.topology.repository.TopologyRepository;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TopologyServiceImpl implements TopologyService {

  private final TopologyRepository topologyRepository;
  private final AppUserRepository appUserRepository;
  private final ObjectMapper objectMapper;
  private final AuditLogService auditLogService;

  public TopologyServiceImpl(
      TopologyRepository topologyRepository,
      AppUserRepository appUserRepository,
      ObjectMapper objectMapper,
      AuditLogService auditLogService
  ) {
    this.topologyRepository = topologyRepository;
    this.appUserRepository = appUserRepository;
    this.objectMapper = objectMapper;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TopologyDto> listAccessible(Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    List<TopologyEntity> entities;
    if (admin) {
      entities = topologyRepository.findAll(Sort.by(Order.desc("updatedAt")));
    } else {
      entities = topologyRepository.findAllAccessibleByUserId(actor.getId());
    }
    return entities.stream()
        .sorted(Comparator.comparing(TopologyEntity::getUpdatedAt).reversed())
        .map(this::toDto)
        .toList();
  }

  @Override
  @Transactional
  public TopologyDto create(TopologyCreateRequest request, Authentication authentication) {
    AppUser owner = requireCurrentUser(authentication);
    validateDocument(request.document());
    Set<Long> shared = normalizeSharedUserIds(request.sharedUserIds(), owner.getId());
    applyVisibilityToShared(request.visibility(), shared);
    validateUserIdsExist(shared);

    TopologyEntity entity = new TopologyEntity();
    entity.setOwner(owner);
    entity.setName(request.name().strip());
    entity.setVisibility(request.visibility());
    entity.setAutosave(request.autosave());
    entity.setAutoCenterOnResize(request.autoCenterOnResize());
    entity.setSharedUserIds(shared);
    entity.setDocumentJson(writeDocument(request.document()));

    TopologyEntity saved = topologyRepository.save(entity);
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.CREATE,
        "id=" + saved.getId() + ", name=" + saved.getName(),
        null
    );
    return toDto(saved);
  }

  @Override
  @Transactional
  public TopologyDto update(Long id, TopologyUpdateRequest request, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    TopologyEntity entity = topologyRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Топология не найдена."));
    if (!canModify(entity, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Изменять топологию может только владелец или администратор.");
    }

    validateDocument(request.document());
    Set<Long> shared = normalizeSharedUserIds(request.sharedUserIds(), entity.getOwner().getId());
    applyVisibilityToShared(request.visibility(), shared);
    validateUserIdsExist(shared);

    entity.setName(request.name().strip());
    entity.setVisibility(request.visibility());
    entity.setAutosave(request.autosave());
    Boolean autoCenterOnResize = request.autoCenterOnResize();
    if (autoCenterOnResize != null) {
      entity.setAutoCenterOnResize(autoCenterOnResize);
    }
    entity.setSharedUserIds(shared);
    entity.setDocumentJson(writeDocument(request.document()));
    if (request.rootLayerBackdropColor() != null) {
      applyRootLayerBackdropColor(entity, request.rootLayerBackdropColor());
    }
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.UPDATE,
        "id=" + id + ", name=" + entity.getName(),
        null
    );
    return toDto(entity);
  }

  @Override
  @Transactional
  public void delete(Long id, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    TopologyEntity entity = topologyRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Топология не найдена."));
    if (!canModify(entity, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Удалять топологию может только владелец или администратор.");
    }
    String topologyName = entity.getName();
    topologyRepository.delete(entity);
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.DELETE,
        "id=" + id + ", name=" + topologyName,
        null
    );
  }

  @Override
  @Transactional(readOnly = true)
  public TopologyDto getById(Long id, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    TopologyEntity entity = topologyRepository.findFetchedById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Топология не найдена."));
    if (!canRead(entity, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этой топологии.");
    }
    return toDto(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isReadableByUser(Long topologyId, Long userId, boolean admin) {
    if (topologyId == null || userId == null) {
      return false;
    }
    TopologyEntity entity = topologyRepository.findById(topologyId).orElse(null);
    if (entity == null) {
      return false;
    }
    return canRead(entity, userId, admin);
  }

  private TopologyDto toDto(TopologyEntity e) {
    return new TopologyDto(
        e.getId(),
        e.getOwnerId(),
        e.getName(),
        e.getVisibility(),
        e.isAutosave(),
        e.isAutoCenterOnResize(),
        new LinkedHashSet<>(e.getSharedUserIds()),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        readDocument(e.getDocumentJson()),
        e.getRootLayerBackdropColor()
    );
  }

  private void applyRootLayerBackdropColor(TopologyEntity entity, String raw) {
    String stripped = raw.strip();
    if (stripped.isEmpty()) {
      entity.setRootLayerBackdropColor(null);
    } else {
      entity.setRootLayerBackdropColor(LayerBackgroundSupport.normalizeHexColorOrThrow(stripped));
    }
  }

  private TopologyEntity loadReadableTopology(Long topologyId, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    TopologyEntity t = topologyRepository.findById(topologyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Топология не найдена."));
    if (!canRead(t, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этой топологии.");
    }
    return t;
  }

  private TopologyEntity loadWritableTopology(Long topologyId, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    TopologyEntity t = topologyRepository.findById(topologyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Топология не найдена."));
    if (!canModify(t, actor.getId(), admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Изменять топологию может только владелец или администратор.");
    }
    return t;
  }

  private JsonNode readDocument(String json) {
    if (json == null || json.isBlank()) {
      return objectMapper.createObjectNode();
    }
    try {
      JsonNode node = objectMapper.readTree(json);
      return node.isObject() ? node : objectMapper.createObjectNode();
    } catch (JsonProcessingException e) {
      return objectMapper.createObjectNode();
    }
  }

  private String writeDocument(JsonNode document) {
    try {
      return objectMapper.writeValueAsString(document);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Не удалось сериализовать document.");
    }
  }

  private static void validateDocument(JsonNode document) {
    if (document == null || !document.isObject()) {
      throw new IllegalArgumentException("Поле document должно быть JSON-объектом.");
    }
  }

  private static boolean canRead(TopologyEntity t, Long userId, boolean admin) {
    if (admin) {
      return true;
    }
    if (t.getOwner().getId().equals(userId)) {
      return true;
    }
    return t.getSharedUserIds().contains(userId);
  }

  private static boolean canModify(TopologyEntity t, Long userId, boolean admin) {
    return admin || t.getOwner().getId().equals(userId);
  }

  private static void applyVisibilityToShared(TopologyVisibility visibility, Set<Long> shared) {
    if (visibility == TopologyVisibility.PRIVATE) {
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
