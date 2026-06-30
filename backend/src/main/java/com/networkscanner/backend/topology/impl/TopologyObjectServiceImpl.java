package com.networkscanner.backend.topology.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.topology.api.TopologyObjectService;
import com.networkscanner.backend.topology.dto.TopologyLayerBackgroundBytes;
import com.networkscanner.backend.topology.dto.TopologyLayoutBatchUpdateRequest;
import com.networkscanner.backend.topology.dto.TopologyLayoutPatchItem;
import com.networkscanner.backend.topology.dto.TopologyObjectCreateRequest;
import com.networkscanner.backend.topology.dto.TopologyObjectDto;
import com.networkscanner.backend.topology.dto.TopologyObjectUpdateRequest;
import com.networkscanner.backend.topology.model.AbstractTopologyObject;
import com.networkscanner.backend.topology.model.TopologyEdgeObject;
import com.networkscanner.backend.topology.model.TopologyEntity;
import com.networkscanner.backend.topology.model.TopologyGroupObject;
import com.networkscanner.backend.topology.model.TopologyObjectLayerBackground;
import com.networkscanner.backend.topology.model.TopologyNodeKind;
import com.networkscanner.backend.topology.model.TopologyNodeObject;
import com.networkscanner.backend.topology.model.TopologyObjectKind;
import com.networkscanner.backend.topology.repository.TopologyObjectLayerBackgroundRepository;
import com.networkscanner.backend.topology.repository.TopologyObjectRepository;
import com.networkscanner.backend.topology.repository.TopologyRepository;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import org.hibernate.Hibernate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TopologyObjectServiceImpl implements TopologyObjectService {

  /** Значения статуса устройства в мониторинге (как в сервисе обновления доступности). */
  private static final String MONITORING_DEVICE_STATUS_UP = "Включено";
  private static final String MONITORING_DEVICE_STATUS_DOWN = "Недоступно";

  private final TopologyRepository topologyRepository;
  private final TopologyObjectRepository topologyObjectRepository;
  private final TopologyObjectLayerBackgroundRepository layerBackgroundRepository;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final AppUserRepository appUserRepository;
  private final AuditLogService auditLogService;

  public TopologyObjectServiceImpl(
      TopologyRepository topologyRepository,
      TopologyObjectRepository topologyObjectRepository,
      TopologyObjectLayerBackgroundRepository layerBackgroundRepository,
      MonitoredDeviceRepository monitoredDeviceRepository,
      AppUserRepository appUserRepository,
      AuditLogService auditLogService
  ) {
    this.topologyRepository = topologyRepository;
    this.topologyObjectRepository = topologyObjectRepository;
    this.layerBackgroundRepository = layerBackgroundRepository;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.appUserRepository = appUserRepository;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TopologyObjectDto> listByTopology(
      Long topologyId,
      Long layerId,
      Authentication authentication
  ) {
    TopologyEntity topology = loadReadableTopology(topologyId, authentication);
    List<AbstractTopologyObject> objects = layerId == null
        ? topologyObjectRepository.findByTopology_IdAndLayerIsNullOrderById(topology.getId())
        : topologyObjectRepository.findByTopology_IdAndLayer_IdOrderById(topology.getId(), layerId);
    Map<Long, MonitoredDeviceEntity> devicesById = loadMonitoringDevicesForTopologyNodes(objects);
    return objects.stream()
        .map(o -> toDto(o, devicesById))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public TopologyObjectDto getById(Long topologyId, Long objectId, Authentication authentication) {
    TopologyEntity topology = loadReadableTopology(topologyId, authentication);
    AbstractTopologyObject obj = topologyObjectRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден."));
    if (!topology.getId().equals(obj.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объект не принадлежит этой топологии.");
    }
    return toDto(obj);
  }

  @Override
  @Transactional
  public TopologyObjectDto create(
      Long topologyId,
      TopologyObjectCreateRequest request,
      Authentication authentication
  ) {
    TopologyEntity topology = loadWritableTopology(topologyId, authentication);
    String elementId = normalizeElementId(request.elementId());

    Optional<AbstractTopologyObject> duplicate =
        topologyObjectRepository.findByTopology_IdAndElementId(topology.getId(), elementId);
    if (duplicate.isPresent()) {
      AbstractTopologyObject existing = duplicate.get();
      if (existing.getType() != request.kind()) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "В топологии уже есть элемент с elementId «"
                + elementId
                + "» (тип "
                + existing.getType()
                + "), создать объект другого типа с тем же id нельзя.");
      }
      return toDto(existing);
    }

    TopologyObjectDto dto = switch (request.kind()) {
      case NODE -> toDto(createNode(topology, request, elementId));
      case EDGE -> toDto(createEdge(topology, request, elementId));
      case GROUP -> toDto(createGroup(topology, request, elementId));
    };
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.CREATE,
        "topologyId=" + topologyId + ", objectId=" + dto.id(),
        "Добавлен объект схемы (" + dto.kind() + ")"
    );
    return dto;
  }

  @Override
  @Transactional
  public TopologyObjectDto update(
      Long topologyId,
      Long objectId,
      TopologyObjectUpdateRequest request,
      Authentication authentication
  ) {
    TopologyEntity topology = loadWritableTopology(topologyId, authentication);
    if (request.positionX() == null
        && request.positionY() == null
        && request.name() == null
        && request.frameWidth() == null
        && request.frameHeight() == null
        && request.frameBorderColor() == null
        && request.layerBackdropColor() == null
        && request.lineColor() == null
        && request.groupId() == null
        && !Boolean.TRUE.equals(request.clearGroup())
        && request.nodeKind() == null
        && request.deviceId() == null
        && !Boolean.TRUE.equals(request.clearDevice())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет данных для обновления.");
    }
    AbstractTopologyObject obj = topologyObjectRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден."));
    if (!topology.getId().equals(obj.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объект не принадлежит этой топологии.");
    }
    if ((request.frameWidth() != null || request.frameHeight() != null || request.frameBorderColor() != null)
        && !(obj instanceof TopologyGroupObject)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Поля frameWidth, frameHeight и frameBorderColor допустимы только для объекта типа GROUP."
      );
    }
    if (request.layerBackdropColor() != null
        && !(obj instanceof TopologyGroupObject) && !(obj instanceof TopologyNodeObject)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Поле layerBackdropColor допустимо только для узла (NODE) или группы (GROUP)."
      );
    }
    if (request.lineColor() != null && !(obj instanceof TopologyEdgeObject)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Поле lineColor допустимо только для объекта типа EDGE."
      );
    }
    boolean hasPosition = request.positionX() != null || request.positionY() != null;
    if (hasPosition) {
      if (obj instanceof TopologyNodeObject node) {
        if (request.positionX() != null) {
          node.setPositionX(request.positionX());
        }
        if (request.positionY() != null) {
          node.setPositionY(request.positionY());
        }
      } else if (obj instanceof TopologyGroupObject grp) {
        if (request.positionX() != null) {
          grp.setPositionX(request.positionX());
        }
        if (request.positionY() != null) {
          grp.setPositionY(request.positionY());
        }
      } else {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Позицию на графе можно задать только для узла (NODE) или группы (GROUP)."
        );
      }
    }
    if (obj instanceof TopologyGroupObject grp2) {
      if (request.frameWidth() != null) {
        if (request.frameWidth() <= 0) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ширина группы должна быть положительной.");
        }
        grp2.setFrameWidth(request.frameWidth());
      }
      if (request.frameHeight() != null) {
        if (request.frameHeight() <= 0) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Высота группы должна быть положительной.");
        }
        grp2.setFrameHeight(request.frameHeight());
      }
      if (request.frameBorderColor() != null) {
        String stripped = request.frameBorderColor().strip();
        if (stripped.isEmpty()) {
          grp2.setFrameBorderColor(null);
        } else {
          grp2.setFrameBorderColor(LayerBackgroundSupport.normalizeHexColorOrThrow(stripped));
        }
      }
    }
    if (request.layerBackdropColor() != null) {
      String stripped = request.layerBackdropColor().strip();
      if (obj instanceof TopologyGroupObject gbd) {
        if (stripped.isEmpty()) {
          gbd.setLayerBackdropColor(null);
        } else {
          gbd.setLayerBackdropColor(LayerBackgroundSupport.normalizeHexColorOrThrow(stripped));
        }
      } else if (obj instanceof TopologyNodeObject nbd) {
        if (stripped.isEmpty()) {
          nbd.setLayerBackdropColor(null);
        } else {
          nbd.setLayerBackdropColor(LayerBackgroundSupport.normalizeHexColorOrThrow(stripped));
        }
      }
    }
    if (obj instanceof TopologyEdgeObject edgeObj && request.lineColor() != null) {
      String stripped = request.lineColor().strip();
      if (stripped.isEmpty()) {
        edgeObj.setLineColor(null);
      } else {
        edgeObj.setLineColor(LayerBackgroundSupport.normalizeHexColorOrThrow(stripped));
      }
    }
    if (request.name() != null) {
      String n = request.name().strip();
      obj.setName(n.isEmpty() ? null : n);
    }
    if (request.groupId() != null && Boolean.TRUE.equals(request.clearGroup())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Укажите либо groupId, либо clearGroup, но не оба сразу."
      );
    }
    if (request.groupId() != null) {
      applyAssignToGroup(obj, topology, request.groupId());
    } else if (Boolean.TRUE.equals(request.clearGroup())) {
      obj.setGroup(null);
    }
    if (request.nodeKind() != null || request.deviceId() != null || Boolean.TRUE.equals(request.clearDevice())) {
      if (!(obj instanceof TopologyNodeObject node)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Поля nodeKind, deviceId и clearDevice допустимы только для объекта типа NODE."
        );
      }
      if (request.deviceId() != null && Boolean.TRUE.equals(request.clearDevice())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Укажите либо deviceId, либо clearDevice, но не оба сразу."
        );
      }
      if (request.nodeKind() != null) {
        node.setNodeKind(request.nodeKind());
      }
      if (request.deviceId() != null) {
        MonitoredDeviceEntity device = monitoredDeviceRepository.findById(request.deviceId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Устройство с id " + request.deviceId() + " не найдено."
            ));
        node.setDevice(device);
      } else if (Boolean.TRUE.equals(request.clearDevice())) {
        node.setDevice(null);
      }
    }
    TopologyObjectDto dto = toDto(topologyObjectRepository.save(obj));
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.UPDATE,
        "topologyId=" + topologyId + ", objectId=" + dto.id(),
        "Изменён объект схемы (" + dto.kind() + ")"
    );
    return dto;
  }

  @Override
  @Transactional
  public void applyLayoutBatch(
      Long topologyId,
      TopologyLayoutBatchUpdateRequest request,
      Authentication authentication
  ) {
    TopologyEntity topology = loadWritableTopology(topologyId, authentication);
    for (TopologyLayoutPatchItem item : request.items()) {
      boolean hasAny = item.positionX() != null
          || item.positionY() != null
          || item.frameWidth() != null
          || item.frameHeight() != null;
      if (!hasAny) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Для объекта " + item.objectId() + " не переданы поля раскладки."
        );
      }
      AbstractTopologyObject obj = topologyObjectRepository
          .findByIdAndTopology_Id(item.objectId(), topology.getId())
          .orElseThrow(() -> layoutBatchObjectResolveException(item.objectId(), topology.getId()));
      applyLayoutPatch(obj, item);
    }
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.UPDATE,
        "topologyId=" + topologyId + ", layoutBatchSize=" + request.items().size(),
        "Пакетное сохранение раскладки топологии"
    );
  }

  /**
   * Объект не найден или относится к другой топологии — отдельно от «не найден», чтобы проще отлаживать клиент.
   */
  private ResponseStatusException layoutBatchObjectResolveException(Long objectId, Long expectedTopologyId) {
    Optional<AbstractTopologyObject> any = topologyObjectRepository.findById(objectId);
    if (any.isEmpty()) {
      return new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден: " + objectId);
    }
    Long actualTid = any.get().getTopologyId();
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Объект objectId="
            + objectId
            + " относится к топологии id="
            + actualTid
            + ", ожидалась id="
            + expectedTopologyId
            + ". Проверьте, что в пакет сохранения не попали id из другой схемы.");
  }

  private static void applyLayoutPatch(AbstractTopologyObject obj, TopologyLayoutPatchItem item) {
    // Прокси может быть подклассом AbstractTopologyObject, а не TopologyGroupObject — иначе ClassCastException.
    AbstractTopologyObject e = (AbstractTopologyObject) Hibernate.unproxy(obj);
    TopologyObjectKind kind = e.getType();
    if (kind == TopologyObjectKind.GROUP) {
      TopologyGroupObject grp = (TopologyGroupObject) e;
      if (item.frameWidth() != null) {
        if (item.frameWidth() <= 0) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ширина группы должна быть положительной.");
        }
        grp.setFrameWidth(item.frameWidth());
      }
      if (item.frameHeight() != null) {
        if (item.frameHeight() <= 0) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Высота группы должна быть положительной.");
        }
        grp.setFrameHeight(item.frameHeight());
      }
      if (item.positionX() != null) {
        grp.setPositionX(item.positionX());
      }
      if (item.positionY() != null) {
        grp.setPositionY(item.positionY());
      }
    } else if (kind == TopologyObjectKind.NODE) {
      TopologyNodeObject node = (TopologyNodeObject) e;
      // В одном пакете с группами клиент иногда передаёт frame* для id узла — для NODE эти поля игнорируем.
      if (item.positionX() != null) {
        node.setPositionX(item.positionX());
      }
      if (item.positionY() != null) {
        node.setPositionY(item.positionY());
      }
    } else {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Пакетная раскладка поддерживает только NODE и GROUP (objectId="
              + e.getId()
              + ", тип="
              + kind
              + ")."
      );
    }
  }

  private void applyAssignToGroup(AbstractTopologyObject obj, TopologyEntity topology, Long groupObjectId) {
    AbstractTopologyObject raw = topologyObjectRepository.findById(groupObjectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Группа с указанным id не найдена."));
    AbstractTopologyObject parentEntity = (AbstractTopologyObject) Hibernate.unproxy(raw);
    if (!topology.getId().equals(parentEntity.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Группа принадлежит другой топологии.");
    }
    if (!(parentEntity instanceof TopologyGroupObject newParent)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupId должен указывать на объект типа GROUP.");
    }
    AbstractTopologyObject moved = (AbstractTopologyObject) Hibernate.unproxy(obj);
    if (moved.getId().equals(newParent.getId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нельзя назначить объект родителем самого себя.");
    }
    if (moved instanceof TopologyGroupObject draggedGroup) {
      if (groupIsStrictAncestorOf(draggedGroup, newParent)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Нельзя поместить группу внутрь самой себя или своей вложенной группы."
        );
      }
    }
    moved.setGroup(newParent);
  }

  /**
   * {@code possibleDescendant} лежит в поддереве групп над {@code ancestor} (т.е. является вложенной в ancestor).
   */
  private static boolean groupIsStrictAncestorOf(TopologyGroupObject ancestor, TopologyGroupObject possibleDescendant) {
    AbstractTopologyObject cur = possibleDescendant.getGroup();
    while (cur != null) {
      if (cur.getId().equals(ancestor.getId())) {
        return true;
      }
      cur = cur.getGroup();
    }
    return false;
  }

  @Override
  @Transactional
  public void delete(Long topologyId, Long objectId, Authentication authentication) {
    TopologyEntity topology = loadWritableTopology(topologyId, authentication);
    AbstractTopologyObject obj = topologyObjectRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден."));
    if (!topology.getId().equals(obj.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объект не принадлежит этой топологии.");
    }
    Long deletedId = obj.getId();
    List<TopologyEdgeObject> incident = topologyObjectRepository.findEdgesIncidentTo(objectId);
    if (!incident.isEmpty()) {
      topologyObjectRepository.deleteAll(incident);
    }
    topologyObjectRepository.delete(obj);
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.DELETE,
        "topologyId=" + topologyId + ", objectId=" + deletedId,
        "Удалён объект схемы"
    );
  }

  @Override
  @Transactional
  public TopologyObjectDto uploadLayerBackground(
      Long topologyId,
      Long objectId,
      byte[] bytes,
      String declaredContentType,
      Authentication authentication
  ) {
    TopologyEntity topology = loadWritableTopology(topologyId, authentication);
    AbstractTopologyObject obj = topologyObjectRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден."));
    if (!topology.getId().equals(obj.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объект не принадлежит этой топологии.");
    }
    if (!(obj instanceof TopologyGroupObject group)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Изображение фона слоя можно задать только для группы (GROUP)."
      );
    }
    Long oid = obj.getId();
    String contentType = LayerBackgroundSupport.resolveLayerBackgroundContentType(bytes, declaredContentType);
    TopologyObjectLayerBackground row = layerBackgroundRepository.findById(oid)
        .orElseGet(TopologyObjectLayerBackground::new);
    row.setObjectId(oid);
    row.setContentType(contentType);
    row.setImageData(bytes);
    layerBackgroundRepository.save(row);
    group.setLayerBackgroundPresent(true);
    topologyObjectRepository.save(group);
    TopologyObjectDto dto = toDto(obj);
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.UPDATE,
        "topologyId=" + topologyId + ", objectId=" + objectId,
        "Загружено изображение фона слоя"
    );
    return dto;
  }

  @Override
  @Transactional
  public void deleteLayerBackground(Long topologyId, Long objectId, Authentication authentication) {
    TopologyEntity topology = loadWritableTopology(topologyId, authentication);
    AbstractTopologyObject obj = topologyObjectRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден."));
    if (!topology.getId().equals(obj.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объект не принадлежит этой топологии.");
    }
    if (!(obj instanceof TopologyGroupObject group)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Фон слоя можно удалить только у группы (GROUP)."
      );
    }
    layerBackgroundRepository.deleteById(obj.getId());
    group.setLayerBackgroundPresent(false);
    topologyObjectRepository.save(group);
    auditLogService.record(
        authentication,
        AuditCategory.TOPOLOGY,
        AuditAction.DELETE,
        "topologyId=" + topologyId + ", objectId=" + objectId,
        "Удалено изображение фона слоя"
    );
  }

  @Override
  @Transactional(readOnly = true)
  public TopologyLayerBackgroundBytes getLayerBackground(
      Long topologyId,
      Long objectId,
      Authentication authentication
  ) {
    TopologyEntity topology = loadReadableTopology(topologyId, authentication);
    AbstractTopologyObject obj = topologyObjectRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Объект топологии не найден."));
    if (!topology.getId().equals(obj.getTopologyId())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Объект не принадлежит этой топологии.");
    }
    if (!(obj instanceof TopologyGroupObject)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Фон слоя доступен только для группы (GROUP)."
      );
    }
    TopologyObjectLayerBackground row = layerBackgroundRepository.findById(objectId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Фон слоя не загружен."));
    return new TopologyLayerBackgroundBytes(row.getImageData(), row.getContentType());
  }

  private TopologyNodeObject createNode(TopologyEntity topology, TopologyObjectCreateRequest req, String elementId) {
    TopologyNodeObject node = new TopologyNodeObject();
    applyCommon(topology, node, elementId, req);
    node.setPositionX(req.positionX());
    node.setPositionY(req.positionY());
    node.setNodeKind(req.nodeKind());
    if (req.deviceId() != null) {
      MonitoredDeviceEntity device = monitoredDeviceRepository.findById(req.deviceId())
          .orElseThrow(() -> new IllegalArgumentException("Устройство с id " + req.deviceId() + " не найдено."));
      node.setDevice(device);
    }
    topology.addObject(node);
    topologyRepository.save(topology);
    return node;
  }

  private TopologyGroupObject createGroup(TopologyEntity topology, TopologyObjectCreateRequest req, String elementId) {
    TopologyGroupObject group = new TopologyGroupObject();
    applyCommon(topology, group, elementId, req);
    double cx = req.positionX() != null ? req.positionX() : 200;
    double cy = req.positionY() != null ? req.positionY() : 200;
    group.setPositionX(cx);
    group.setPositionY(cy);
    double w = req.frameWidth() != null && req.frameWidth() > 0 ? req.frameWidth() : 280;
    double h = req.frameHeight() != null && req.frameHeight() > 0 ? req.frameHeight() : 200;
    group.setFrameWidth(w);
    group.setFrameHeight(h);
    if (req.frameBorderColor() != null && !req.frameBorderColor().isBlank()) {
      group.setFrameBorderColor(LayerBackgroundSupport.normalizeHexColorOrThrow(req.frameBorderColor().strip()));
    }
    topology.addObject(group);
    topologyRepository.save(topology);
    return group;
  }

  private TopologyEdgeObject createEdge(TopologyEntity topology, TopologyObjectCreateRequest req, String elementId) {
    if (req.sourceObjectId() == null || req.targetObjectId() == null) {
      throw new IllegalArgumentException("Для ребра укажите sourceObjectId и targetObjectId.");
    }
    AbstractTopologyObject source = topologyObjectRepository.findById(req.sourceObjectId())
        .orElseThrow(() -> new IllegalArgumentException("Объект-источник не найден."));
    AbstractTopologyObject target = topologyObjectRepository.findById(req.targetObjectId())
        .orElseThrow(() -> new IllegalArgumentException("Объект-получатель не найден."));
    if (!topology.getId().equals(source.getTopologyId()) || !topology.getId().equals(target.getTopologyId())) {
      throw new IllegalArgumentException("Источник и получатель должны принадлежать этой топологии.");
    }
    if (source.getId().equals(target.getId())) {
      throw new IllegalArgumentException("Ребро не может соединять объект с самим собой.");
    }

    TopologyEdgeObject edge = new TopologyEdgeObject();
    applyCommon(topology, edge, elementId, req);
    edge.setSource(source);
    edge.setTarget(target);
    if (req.lineColor() != null && !req.lineColor().isBlank()) {
      edge.setLineColor(LayerBackgroundSupport.normalizeHexColorOrThrow(req.lineColor().strip()));
    }
    topology.addObject(edge);
    topologyRepository.save(topology);
    return edge;
  }

  private void applyCommon(
      TopologyEntity topology,
      AbstractTopologyObject obj,
      String elementId,
      TopologyObjectCreateRequest req
  ) {
    obj.setElementId(elementId);
    obj.setName(req.name());
    obj.setStatus(req.status());
    obj.setDescription(req.description());
    if (req.layerId() != null) {
      AbstractTopologyObject layer = topologyObjectRepository.findById(req.layerId())
          .orElseThrow(() -> new IllegalArgumentException("Объект слоя (layerId) не найден."));
      if (!topology.getId().equals(layer.getTopologyId())) {
        throw new IllegalArgumentException("Слой должен принадлежать этой топологии.");
      }
      obj.setLayer(layer);
    }
    if (req.groupId() != null) {
      AbstractTopologyObject group = topologyObjectRepository.findById(req.groupId())
          .orElseThrow(() -> new IllegalArgumentException("Группа (groupId) не найдена."));
      if (!topology.getId().equals(group.getTopologyId())) {
        throw new IllegalArgumentException("Группа должна принадлежать этой топологии.");
      }
      if (!(group instanceof TopologyGroupObject)) {
        throw new IllegalArgumentException("groupId должен указывать на объект типа GROUP.");
      }
      obj.setGroup(group);
    }
  }

  private static String normalizeElementId(String raw) {
    if (raw == null || raw.isBlank()) {
      return "el-" + UUID.randomUUID();
    }
    return raw.strip();
  }

  private TopologyObjectDto toDto(AbstractTopologyObject o) {
    return toDto(o, loadMonitoringDevicesForTopologyNodes(List.of(o)));
  }

  private TopologyObjectDto toDto(AbstractTopologyObject o, Map<Long, MonitoredDeviceEntity> devicesById) {
    AbstractTopologyObject entity = (AbstractTopologyObject) Hibernate.unproxy(o);
    Double px = null;
    Double py = null;
    TopologyNodeKind nk = null;
    Long deviceId = null;
    String deviceHostAvailability = null;
    String deviceHealthStatus = null;
    Long srcId = null;
    Long tgtId = null;
    String srcEl = null;
    String tgtEl = null;
    Double fw = null;
    Double fh = null;
    String frameBorderColor = null;
    String lineColor = null;
    String layerBackdropColor = null;
    Boolean layerBackgroundPresent = null;

    if (entity instanceof TopologyNodeObject n) {
      px = n.getPositionX();
      py = n.getPositionY();
      nk = n.getNodeKind();
      deviceId = n.getDeviceId();
      layerBackdropColor = n.getLayerBackdropColor();
      if (deviceId != null) {
        MonitoredDeviceEntity d = devicesById.get(deviceId);
        if (d != null) {
          deviceHostAvailability = mapMonitoringStatusToHostAvailability(d.getStatus());
          DeviceHealthStatus hs = d.getHealthStatus();
          deviceHealthStatus = hs == null ? null : hs.name();
        } else {
          deviceHostAvailability = "UNKNOWN";
        }
      }
    } else if (entity instanceof TopologyEdgeObject e) {
      srcId = e.getSource() == null ? null : e.getSource().getId();
      tgtId = e.getTarget() == null ? null : e.getTarget().getId();
      srcEl = e.getSourceElementId();
      tgtEl = e.getTargetElementId();
      lineColor = e.getLineColor();
    } else if (entity instanceof TopologyGroupObject g) {
      px = g.getPositionX();
      py = g.getPositionY();
      fw = g.getFrameWidth();
      fh = g.getFrameHeight();
      frameBorderColor = g.getFrameBorderColor();
      layerBackgroundPresent = g.isLayerBackgroundPresent();
      layerBackdropColor = g.getLayerBackdropColor();
    }

    return new TopologyObjectDto(
        entity.getId(),
        entity.getType(),
        entity.getTopologyId(),
        entity.getElementId(),
        entity.getName(),
        entity.getStatus(),
        entity.getDescription(),
        entity.getLayerId(),
        entity.getGroupId(),
        px,
        py,
        nk,
        deviceId,
        srcId,
        tgtId,
        srcEl,
        tgtEl,
        fw,
        fh,
        frameBorderColor,
        lineColor,
        layerBackdropColor,
        deviceHostAvailability,
        deviceHealthStatus,
        layerBackgroundPresent
    );
  }

  private Map<Long, MonitoredDeviceEntity> loadMonitoringDevicesForTopologyNodes(
      List<AbstractTopologyObject> objects
  ) {
    Set<Long> ids = new HashSet<>();
    for (AbstractTopologyObject o : objects) {
      if (o instanceof TopologyNodeObject n) {
        Long did = n.getDeviceId();
        if (did != null) {
          ids.add(did);
        }
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    return monitoredDeviceRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(MonitoredDeviceEntity::getId, Function.identity()));
  }

  private static String mapMonitoringStatusToHostAvailability(String monitoringStatus) {
    if (monitoringStatus == null) {
      return "UNKNOWN";
    }
    if (MONITORING_DEVICE_STATUS_UP.equals(monitoringStatus)) {
      return "AVAILABLE";
    }
    if (MONITORING_DEVICE_STATUS_DOWN.equals(monitoringStatus)) {
      return "UNAVAILABLE";
    }
    return "UNKNOWN";
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
