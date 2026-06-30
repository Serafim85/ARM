package com.networkscanner.backend.topology.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Логическая группа элементов топологии (контейнер для объединения узлов/рёбер и т.д.).
 * Принадлежность других объектов задаётся полем {@link AbstractTopologyObject#getGroup()} / {@code group_id}.
 *
 * <p>Центр прямоугольника области и размеры в координатах графа (как у узлов Cytoscape).
 */
@Entity
@Table(name = "topology_object_groups")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("GROUP")
public class TopologyGroupObject extends AbstractTopologyObject {

  @Column(name = "position_x", nullable = false)
  private double positionX;

  @Column(name = "position_y", nullable = false)
  private double positionY;

  @Column(name = "frame_width", nullable = false)
  private double frameWidth = 280;

  @Column(name = "frame_height", nullable = false)
  private double frameHeight = 200;

  /** Hex-цвет рамки (#RRGGBB или #RGB); {@code null} — в клиенте используется цвет по умолчанию. */
  @Column(name = "frame_border_color", length = 32)
  private String frameBorderColor;

  /**
   * Есть ли сохранённое изображение фона слоя (см. {@link TopologyObjectLayerBackground}).
   * Дублируется для списка объектов без загрузки BYTEA.
   */
  @Column(name = "layer_background_present", nullable = false)
  private boolean layerBackgroundPresent = false;

  /** Hex цвет подложки слоя (#RRGGBB / #RGB); {@code null} — без заливки в UI. */
  @Column(name = "layer_backdrop_color", length = 32)
  private String layerBackdropColor;

  public double getPositionX() {
    return positionX;
  }

  public void setPositionX(double positionX) {
    this.positionX = positionX;
  }

  public double getPositionY() {
    return positionY;
  }

  public void setPositionY(double positionY) {
    this.positionY = positionY;
  }

  public double getFrameWidth() {
    return frameWidth;
  }

  public void setFrameWidth(double frameWidth) {
    this.frameWidth = frameWidth;
  }

  public double getFrameHeight() {
    return frameHeight;
  }

  public void setFrameHeight(double frameHeight) {
    this.frameHeight = frameHeight;
  }

  public String getFrameBorderColor() {
    return frameBorderColor;
  }

  public void setFrameBorderColor(String frameBorderColor) {
    this.frameBorderColor = frameBorderColor;
  }

  public boolean isLayerBackgroundPresent() {
    return layerBackgroundPresent;
  }

  public void setLayerBackgroundPresent(boolean layerBackgroundPresent) {
    this.layerBackgroundPresent = layerBackgroundPresent;
  }

  public String getLayerBackdropColor() {
    return layerBackdropColor;
  }

  public void setLayerBackdropColor(String layerBackdropColor) {
    this.layerBackdropColor = layerBackdropColor;
  }

  @Override
  public TopologyObjectKind getType() {
    return TopologyObjectKind.GROUP;
  }
}
