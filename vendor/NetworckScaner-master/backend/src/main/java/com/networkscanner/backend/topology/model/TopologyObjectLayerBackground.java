package com.networkscanner.backend.topology.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Байты фона слоя для узла или группы ({@link TopologyNodeObject}, {@link TopologyGroupObject});
 * отдельная таблица, чтобы не подтягивать LOB при списке объектов.
 */
@Entity
@Table(name = "topology_object_layer_backgrounds")
public class TopologyObjectLayerBackground {

  @Id
  @Column(name = "object_id")
  private Long objectId;

  @Column(name = "content_type", nullable = false, length = 64)
  private String contentType;

  @Column(name = "image_data", nullable = false, columnDefinition = "BYTEA")
  private byte[] imageData;

  public Long getObjectId() {
    return objectId;
  }

  public void setObjectId(Long objectId) {
    this.objectId = objectId;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public byte[] getImageData() {
    return imageData;
  }

  public void setImageData(byte[] imageData) {
    this.imageData = imageData;
  }
}
