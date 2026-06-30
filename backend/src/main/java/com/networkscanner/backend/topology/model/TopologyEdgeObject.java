package com.networkscanner.backend.topology.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Ребро графа: обязательные для Cytoscape концы {@code data.source} и {@code data.target}
 * (ссылки на {@link AbstractTopologyObject}, как правило узлы {@link TopologyNodeObject}).
 */
@Entity
@Table(name = "topology_object_edges")
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("EDGE")
public class TopologyEdgeObject extends AbstractTopologyObject {

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private AbstractTopologyObject source;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "target_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private AbstractTopologyObject target;

  /** Цвет линии и стрелки в UI (#RRGGBB); {@code null} — по умолчанию. */
  @Column(name = "line_color", length = 32)
  private String lineColor;

  public AbstractTopologyObject getSource() {
    return source;
  }

  public void setSource(AbstractTopologyObject source) {
    this.source = source;
  }

  public AbstractTopologyObject getTarget() {
    return target;
  }

  public void setTarget(AbstractTopologyObject target) {
    this.target = target;
  }

  public String getLineColor() {
    return lineColor;
  }

  public void setLineColor(String lineColor) {
    this.lineColor = lineColor;
  }

  /** Cytoscape {@code data.source}: {@code elementId} начального узла. */
  public String getSourceElementId() {
    return source == null ? null : source.getElementId();
  }

  /** Cytoscape {@code data.target}: {@code elementId} конечного узла. */
  public String getTargetElementId() {
    return target == null ? null : target.getElementId();
  }

  @Override
  public TopologyObjectKind getType() {
    return TopologyObjectKind.EDGE;
  }
}
