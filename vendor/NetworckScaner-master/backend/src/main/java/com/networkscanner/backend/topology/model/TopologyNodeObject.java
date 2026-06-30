package com.networkscanner.backend.topology.model;

import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Узел графа: обязательные для Cytoscape позиции {@code position} (x, y) при отображении.
 *
 * <p>{@link #nodeKind} — семантический тип узла; на клиенте по нему (и по тому, что {@link #getType()} возвращает
 * {@link TopologyObjectKind#NODE}) подбирается иконка.
 * <p>{@link #device} — опциональная привязка к устройству на мониторинге.
 */
@Entity
@Table(name = "topology_object_nodes")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("NODE")
public class TopologyNodeObject extends AbstractTopologyObject {

  @Column(name = "position_x")
  private Double positionX;

  @Column(name = "position_y")
  private Double positionY;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "device_id")
  private MonitoredDeviceEntity device;

  @Enumerated(EnumType.STRING)
  @Column(name = "node_kind", length = 32)
  private TopologyNodeKind nodeKind;

  @Column(name = "layer_backdrop_color", length = 32)
  private String layerBackdropColor;

  public Double getPositionX() {
    return positionX;
  }

  public void setPositionX(Double positionX) {
    this.positionX = positionX;
  }

  public Double getPositionY() {
    return positionY;
  }

  public void setPositionY(Double positionY) {
    this.positionY = positionY;
  }

  public MonitoredDeviceEntity getDevice() {
    return device;
  }

  public void setDevice(MonitoredDeviceEntity device) {
    this.device = device;
  }

  public Long getDeviceId() {
    return device == null ? null : device.getId();
  }

  public TopologyNodeKind getNodeKind() {
    return nodeKind;
  }

  public void setNodeKind(TopologyNodeKind nodeKind) {
    this.nodeKind = nodeKind;
  }

  public String getLayerBackdropColor() {
    return layerBackdropColor;
  }

  public void setLayerBackdropColor(String layerBackdropColor) {
    this.layerBackdropColor = layerBackdropColor;
  }

  @Override
  public TopologyObjectKind getType() {
    return TopologyObjectKind.NODE;
  }
}
