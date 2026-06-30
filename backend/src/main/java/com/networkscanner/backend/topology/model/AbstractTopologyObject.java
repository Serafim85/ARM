package com.networkscanner.backend.topology.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Базовый объект элемента топологии (узел, ребро и т.д.).
 *
 * <p>{@link #elementId} — обязательный идентификатор элемента в терминах Cytoscape ({@code data.id}).
 * <p>{@link #layer} — объект-«контейнер» следующего уровня вложенности (родительский слой); по умолчанию {@code null}.
 * <p>{@link #group} — группа ({@link TopologyGroupObject}), к которой относится объект; по умолчанию {@code null}.
 */
@Entity
@Table(name = "topology_objects")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "object_kind", discriminatorType = DiscriminatorType.STRING, length = 32)
public abstract class AbstractTopologyObject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "topology_id", nullable = false)
  private TopologyEntity topology;

  /**
   * Родительский объект топологии, задающий вложенный слой (аналог compound / вложенной сцены).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "layer_id")
  private AbstractTopologyObject layer;

  /**
   * Группа топологии (обычно {@link TopologyGroupObject}), задающая принадлежность объекта к группе.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id")
  private AbstractTopologyObject group;

  /**
   * Строковый id элемента для Cytoscape ({@code data.id}); уникален в пределах одной топологии.
   */
  @Column(name = "element_id", nullable = false, length = 255)
  private String elementId;

  @Column(length = 512)
  private String name;

  @Column(length = 64)
  private String status;

  @Column(columnDefinition = "TEXT")
  private String description;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTopologyId() {
    return topology == null ? null : topology.getId();
  }

  public TopologyEntity getTopology() {
    return topology;
  }

  public void setTopology(TopologyEntity topology) {
    this.topology = topology;
  }

  public AbstractTopologyObject getLayer() {
    return layer;
  }

  public void setLayer(AbstractTopologyObject layer) {
    this.layer = layer;
  }

  public Long getLayerId() {
    return layer == null ? null : layer.getId();
  }

  public AbstractTopologyObject getGroup() {
    return group;
  }

  public void setGroup(AbstractTopologyObject group) {
    this.group = group;
  }

  public Long getGroupId() {
    return group == null ? null : group.getId();
  }

  public String getElementId() {
    return elementId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /** Тип объекта для API и дискриминатора JPA. */
  public abstract TopologyObjectKind getType();
}
