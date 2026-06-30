package com.networkscanner.backend.topology.model;

import com.networkscanner.backend.users.model.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "topologies")
public class TopologyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private AppUser owner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private TopologyVisibility visibility;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private boolean autosave = false;

  @Column(name = "auto_center_on_resize", nullable = false)
  private boolean autoCenterOnResize = true;

  @Column(name = "document_json", nullable = false, columnDefinition = "TEXT")
  private String documentJson = "{}";

  @Column(name = "root_layer_backdrop_color", length = 32)
  private String rootLayerBackdropColor;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "topology_shared_users", joinColumns = @JoinColumn(name = "topology_id"))
  @Column(name = "user_id", nullable = false)
  private Set<Long> sharedUserIds = new LinkedHashSet<>();

  @OneToMany(mappedBy = "topology", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<AbstractTopologyObject> objects = new ArrayList<>();

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public AppUser getOwner() {
    return owner;
  }

  public void setOwner(AppUser owner) {
    this.owner = owner;
  }

  public Long getOwnerId() {
    return owner == null ? null : owner.getId();
  }

  public TopologyVisibility getVisibility() {
    return visibility;
  }

  public void setVisibility(TopologyVisibility visibility) {
    this.visibility = visibility;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isAutosave() {
    return autosave;
  }

  public void setAutosave(boolean autosave) {
    this.autosave = autosave;
  }

  public boolean isAutoCenterOnResize() {
    return autoCenterOnResize;
  }

  public void setAutoCenterOnResize(boolean autoCenterOnResize) {
    this.autoCenterOnResize = autoCenterOnResize;
  }

  public String getDocumentJson() {
    return documentJson;
  }

  public void setDocumentJson(String documentJson) {
    this.documentJson = documentJson;
  }

  public String getRootLayerBackdropColor() {
    return rootLayerBackdropColor;
  }

  public void setRootLayerBackdropColor(String rootLayerBackdropColor) {
    this.rootLayerBackdropColor = rootLayerBackdropColor;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Set<Long> getSharedUserIds() {
    return sharedUserIds;
  }

  public void setSharedUserIds(Set<Long> sharedUserIds) {
    this.sharedUserIds = sharedUserIds;
  }

  public List<AbstractTopologyObject> getObjects() {
    return objects;
  }

  public void setObjects(List<AbstractTopologyObject> objects) {
    this.objects = objects;
  }

  public void addObject(AbstractTopologyObject object) {
    objects.add(object);
    object.setTopology(this);
  }

  public void removeObject(AbstractTopologyObject object) {
    objects.remove(object);
  }
}
