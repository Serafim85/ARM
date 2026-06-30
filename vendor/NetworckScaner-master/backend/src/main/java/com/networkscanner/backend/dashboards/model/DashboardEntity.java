package com.networkscanner.backend.dashboards.model;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "dashboards")
public class DashboardEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private AppUser owner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DashboardVisibility visibility;

  @Column(nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "dashboard_shared_users", joinColumns = @JoinColumn(name = "dashboard_id"))
  @Column(name = "user_id", nullable = false)
  private Set<Long> sharedUserIds = new LinkedHashSet<>();

  @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  private List<AbstractWidgetEntity> widgets = new ArrayList<>();

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

  public DashboardVisibility getVisibility() {
    return visibility;
  }

  public void setVisibility(DashboardVisibility visibility) {
    this.visibility = visibility;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public List<AbstractWidgetEntity> getWidgets() {
    return widgets;
  }

  public void setWidgets(List<AbstractWidgetEntity> widgets) {
    this.widgets = widgets;
  }

  public void addWidget(AbstractWidgetEntity widget) {
    widgets.add(widget);
    widget.setDashboard(this);
  }

  public void removeWidget(AbstractWidgetEntity widget) {
    widgets.remove(widget);
  }
}
