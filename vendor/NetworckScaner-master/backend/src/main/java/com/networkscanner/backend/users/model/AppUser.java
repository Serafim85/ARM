package com.networkscanner.backend.users.model;

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
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class AppUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "default_dashboard_id")
  private Long defaultDashboardId;

  @Column(name = "default_topology_id")
  private Long defaultTopologyId;

  @Column(name = "monitoring_events_columns_json")
  private String monitoringEventsColumnsJson;

  @Column(name = "monitoring_devices_columns_json")
  private String monitoringDevicesColumnsJson;

  @Column(name = "chart_ui_preferences_json")
  private String chartUiPreferencesJson;

  @Column(name = "table_column_widths_json")
  private String tableColumnWidthsJson;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_name", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<RoleName> roles = new LinkedHashSet<>();

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public Long getDefaultDashboardId() {
    return defaultDashboardId;
  }

  public void setDefaultDashboardId(Long defaultDashboardId) {
    this.defaultDashboardId = defaultDashboardId;
  }

  public Long getDefaultTopologyId() {
    return defaultTopologyId;
  }

  public void setDefaultTopologyId(Long defaultTopologyId) {
    this.defaultTopologyId = defaultTopologyId;
  }

  public String getMonitoringEventsColumnsJson() {
    return monitoringEventsColumnsJson;
  }

  public void setMonitoringEventsColumnsJson(String monitoringEventsColumnsJson) {
    this.monitoringEventsColumnsJson = monitoringEventsColumnsJson;
  }

  public String getMonitoringDevicesColumnsJson() {
    return monitoringDevicesColumnsJson;
  }

  public void setMonitoringDevicesColumnsJson(String monitoringDevicesColumnsJson) {
    this.monitoringDevicesColumnsJson = monitoringDevicesColumnsJson;
  }

  public String getChartUiPreferencesJson() {
    return chartUiPreferencesJson;
  }

  public void setChartUiPreferencesJson(String chartUiPreferencesJson) {
    this.chartUiPreferencesJson = chartUiPreferencesJson;
  }

  public String getTableColumnWidthsJson() {
    return tableColumnWidthsJson;
  }

  public void setTableColumnWidthsJson(String tableColumnWidthsJson) {
    this.tableColumnWidthsJson = tableColumnWidthsJson;
  }

  public Set<RoleName> getRoles() {
    return roles;
  }

  public void setRoles(Set<RoleName> roles) {
    this.roles = roles;
  }
}
