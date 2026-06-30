package com.networkscanner.backend.topology.web;

import com.networkscanner.backend.topology.api.TopologyObjectService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.topology.dto.TopologyCreateRequest;
import com.networkscanner.backend.topology.dto.TopologyDto;
import com.networkscanner.backend.topology.dto.TopologyObjectCreateRequest;
import com.networkscanner.backend.topology.dto.TopologyObjectDto;
import com.networkscanner.backend.topology.dto.TopologyLayerBackgroundBytes;
import com.networkscanner.backend.topology.dto.TopologyLayoutBatchUpdateRequest;
import com.networkscanner.backend.topology.dto.TopologyObjectUpdateRequest;
import com.networkscanner.backend.topology.dto.TopologyUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/topologies")
@Tag(
    name = "Топологии",
    description = "Сохранённые схемы сетевой топологии (документ JSON для визуализации). "
        + "Права доступа: владелец и список пользователей при visibility=SHARED; администратор видит и меняет все."
)
public class TopologyController {

  private final TopologyService topologyService;
  private final TopologyObjectService topologyObjectService;

  public TopologyController(TopologyService topologyService, TopologyObjectService topologyObjectService) {
    this.topologyService = topologyService;
    this.topologyObjectService = topologyObjectService;
  }

  @GetMapping
  @Operation(
      summary = "Список доступных топологий",
      description = "Топологии текущего пользователя (владелец или в shared) и все записи для ADMIN. "
          + "Каждая запись включает полный document."
  )
  public List<TopologyDto> list(Authentication authentication) {
    return topologyService.listAccessible(authentication);
  }

  @PostMapping
  @Operation(summary = "Создать топологию", description = "Владелец — текущий пользователь. Поле document — JSON-объект (например экспорт Cytoscape).")
  public TopologyDto create(
      @Valid @RequestBody TopologyCreateRequest request,
      Authentication authentication
  ) {
    return topologyService.create(request, authentication);
  }

  @PutMapping("/{id:\\d+}")
  @Operation(
      summary = "Изменить топологию",
      description = "Только владелец записи или пользователь с ролью ADMIN. "
          + "Опционально rootLayerBackdropColor: пустая строка сбрасывает цвет подложки корневого слоя, "
          + "null в JSON — не менять."
  )
  public TopologyDto update(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long id,
      @Valid @RequestBody TopologyUpdateRequest request,
      Authentication authentication
  ) {
    return topologyService.update(id, request, authentication);
  }

  @DeleteMapping("/{id:\\d+}")
  @Operation(
      summary = "Удалить топологию",
      description = "Только владелец записи или пользователь с ролью ADMIN."
  )
  public void delete(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long id,
      Authentication authentication
  ) {
    topologyService.delete(id, authentication);
  }

  @GetMapping("/{id:\\d+}")
  @Operation(
      summary = "Получить топологию по id",
      description = "Доступ: владелец, пользователь из списка sharedUserIds или ADMIN."
  )
  public TopologyDto getById(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long id,
      Authentication authentication
  ) {
    return topologyService.getById(id, authentication);
  }

  @GetMapping("/{topologyId:\\d+}/objects")
  @Operation(
      summary = "Список объектов топологии",
      description = "Узлы, рёбра и группы, сохранённые для указанной топологии. "
          + "При layerId отдаёт только дочерний уровень указанного родителя, без layerId — корневой уровень (layer_id is null). "
          + "Доступ как у GET топологии."
  )
  public List<TopologyObjectDto> listObjects(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Фильтр по родительскому объекту уровня (layer_id); null = корневой уровень")
      @RequestParam(required = false) Long layerId,
      Authentication authentication
  ) {
    return topologyObjectService.listByTopology(topologyId, layerId, authentication);
  }

  @GetMapping("/{topologyId:\\d+}/objects/{objectId:\\d+}")
  @Operation(
      summary = "Получить объект топологии по id",
      description = "Доступ как у списка объектов. Удобно для диалогов без загрузки всего уровня."
  )
  public TopologyObjectDto getObject(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Идентификатор объекта (topology_objects.id)") @PathVariable Long objectId,
      Authentication authentication
  ) {
    return topologyObjectService.getById(topologyId, objectId, authentication);
  }

  @PostMapping("/{topologyId:\\d+}/objects")
  @Operation(
      summary = "Добавить объект топологии",
      description = "Создаёт узел (NODE), ребро (EDGE) или группу (GROUP). Для EDGE обязательны sourceObjectId и targetObjectId "
          + "(id строк в topology_objects); опционально lineColor (#RRGGBB / #RGB). elementId можно не задавать — будет сгенерирован."
  )
  public TopologyObjectDto createObject(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Valid @RequestBody TopologyObjectCreateRequest request,
      Authentication authentication
  ) {
    return topologyObjectService.create(topologyId, request, authentication);
  }

  @PutMapping("/{topologyId:\\d+}/objects/{objectId:\\d+}")
  @Operation(
      summary = "Изменить объект топологии",
      description = "Частичное обновление: имя; для NODE — центр узла (positionX/Y), layerBackdropColor (подложка слоя); "
          + "для GROUP — центр рамки и/или размеры frameWidth/frameHeight, "
          + "цвет рамки frameBorderColor (#RRGGBB / #RGB) или пустая строка для сброса к умолчанию, layerBackdropColor; "
          + "для EDGE — lineColor (#RRGGBB / #RGB) или пустая строка для сброса; "
          + "принадлежность к группе — groupId (родитель типа GROUP) или clearGroup=true. "
          + "Только владелец топологии или ADMIN."
  )
  public TopologyObjectDto updateObject(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Идентификатор объекта (строка topology_objects)") @PathVariable Long objectId,
      @Valid @RequestBody TopologyObjectUpdateRequest request,
      Authentication authentication
  ) {
    return topologyObjectService.update(topologyId, objectId, request, authentication);
  }

  @PutMapping("/{topologyId:\\d+}/objects/layout-batch")
  @Operation(
      summary = "Пакетно сохранить раскладку (координаты и рамки групп)",
      description = "Одна транзакция БД: для NODE — positionX/positionY (центр); для GROUP — центр и/или frameWidth/frameHeight. "
          + "Снижает риск «рваной» схемы после обновления страницы при частичном успехе цепочки одиночных PUT. "
          + "Только владелец топологии или ADMIN."
  )
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void applyLayoutBatch(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Valid @RequestBody TopologyLayoutBatchUpdateRequest request,
      Authentication authentication
  ) {
    topologyObjectService.applyLayoutBatch(topologyId, request, authentication);
  }

  @DeleteMapping("/{topologyId:\\d+}/objects/{objectId:\\d+}")
  @Operation(
      summary = "Удалить объект топологии",
      description = "Узел, ребро или группа. Связанные рёбра (инцидентные удаляемому объекту) удаляются вместе с ним. "
          + "Только владелец топологии или ADMIN."
  )
  public void deleteObject(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Идентификатор объекта") @PathVariable Long objectId,
      Authentication authentication
  ) {
    topologyObjectService.delete(topologyId, objectId, authentication);
  }

  @PostMapping(
      value = "/{topologyId:\\d+}/objects/{objectId:\\d+}/layer-background",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  @Operation(
      summary = "Загрузить фон слоя группы (GROUP)",
      description = "Форматы: PNG, JPEG, SVG. До 5 МБ. Только владелец топологии или ADMIN."
  )
  public TopologyObjectDto uploadLayerBackground(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Идентификатор группы (topology_objects.id)") @PathVariable Long objectId,
      @Parameter(description = "Файл изображения") @RequestParam("file") MultipartFile file,
      Authentication authentication
  ) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите файл.");
    }
    try {
      return topologyObjectService.uploadLayerBackground(
          topologyId,
          objectId,
          file.getBytes(),
          file.getContentType(),
          authentication
      );
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать файл.");
    }
  }

  @DeleteMapping("/{topologyId:\\d+}/objects/{objectId:\\d+}/layer-background")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Удалить фон слоя группы (GROUP)",
      description = "Только владелец топологии или ADMIN."
  )
  public void deleteLayerBackground(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Идентификатор группы") @PathVariable Long objectId,
      Authentication authentication
  ) {
    topologyObjectService.deleteLayerBackground(topologyId, objectId, authentication);
  }

  @GetMapping("/{topologyId:\\d+}/objects/{objectId:\\d+}/layer-background")
  @Operation(
      summary = "Скачать фон слоя группы (GROUP)",
      description = "Доступ как у GET топологии (владелец, shared или ADMIN)."
  )
  public ResponseEntity<byte[]> getLayerBackground(
      @Parameter(description = "Идентификатор топологии") @PathVariable Long topologyId,
      @Parameter(description = "Идентификатор группы") @PathVariable Long objectId,
      Authentication authentication
  ) {
    TopologyLayerBackgroundBytes body = topologyObjectService.getLayerBackground(topologyId, objectId, authentication);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, body.contentType())
        .body(body.data());
  }
}
