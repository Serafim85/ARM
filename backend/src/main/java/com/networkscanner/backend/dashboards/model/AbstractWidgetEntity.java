package com.networkscanner.backend.dashboards.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dashboard_widgets")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "widget_type", discriminatorType = DiscriminatorType.STRING, length = 50)
public abstract class AbstractWidgetEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "dashboard_id", nullable = false)
  private DashboardEntity dashboard;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private String name = "";

  @Column(name = "grid_x", nullable = false)
  private int gridX;

  @Column(name = "grid_y", nullable = false)
  private int gridY;

  @Column(name = "width", nullable = false)
  private int width = 1;

  @Column(name = "height", nullable = false)
  private int height = 2;

  @Column(name = "view_mode", nullable = false)
  private int viewMode;

  @Column(name = "refresh_interval_seconds")
  private Integer refreshIntervalSeconds;

  @Column(name = "show_header", nullable = false)
  private boolean showHeader = true;

  @Column(name = "border_width_px", nullable = false)
  private int borderWidthPx = 1;

  @Column(name = "border_color", nullable = false, length = 64)
  private String borderColor = "gray";

  @OneToMany(mappedBy = "widget", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<DashboardWidgetFieldEntity> fields = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public DashboardEntity getDashboard() {
    return dashboard;
  }

  public void setDashboard(DashboardEntity dashboard) {
    this.dashboard = dashboard;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getGridX() {
    return gridX;
  }

  public void setGridX(int gridX) {
    this.gridX = gridX;
  }

  public int getGridY() {
    return gridY;
  }

  public void setGridY(int gridY) {
    this.gridY = gridY;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public int getViewMode() {
    return viewMode;
  }

  public void setViewMode(int viewMode) {
    this.viewMode = viewMode;
  }

  public Integer getRefreshIntervalSeconds() {
    return refreshIntervalSeconds;
  }

  public void setRefreshIntervalSeconds(Integer refreshIntervalSeconds) {
    this.refreshIntervalSeconds = refreshIntervalSeconds;
  }

  public boolean isShowHeader() {
    return showHeader;
  }

  public void setShowHeader(boolean showHeader) {
    this.showHeader = showHeader;
  }

  public int getBorderWidthPx() {
    return borderWidthPx;
  }

  public void setBorderWidthPx(int borderWidthPx) {
    this.borderWidthPx = borderWidthPx;
  }

  public String getBorderColor() {
    return borderColor;
  }

  public void setBorderColor(String borderColor) {
    this.borderColor = borderColor;
  }

  public List<DashboardWidgetFieldEntity> getFields() {
    return fields;
  }

  public void setFields(List<DashboardWidgetFieldEntity> fields) {
    this.fields = fields;
  }

  public void addField(DashboardWidgetFieldEntity field) {
    fields.add(field);
    field.setWidget(this);
  }

  public void clearFields() {
    fields.clear();
  }
}
