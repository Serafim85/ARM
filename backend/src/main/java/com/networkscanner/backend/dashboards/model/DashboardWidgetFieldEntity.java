package com.networkscanner.backend.dashboards.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dashboard_widget_fields")
public class DashboardWidgetFieldEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "widget_id", nullable = false)
  private AbstractWidgetEntity widget;

  @Column(nullable = false)
  private String name;

  @Column(name = "value_int", nullable = false)
  private int valueInt;

  @Column(name = "value_str", nullable = false, length = 2048)
  private String valueStr = "";

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public AbstractWidgetEntity getWidget() {
    return widget;
  }

  public void setWidget(AbstractWidgetEntity widget) {
    this.widget = widget;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getValueInt() {
    return valueInt;
  }

  public void setValueInt(int valueInt) {
    this.valueInt = valueInt;
  }

  public String getValueStr() {
    return valueStr;
  }

  public void setValueStr(String valueStr) {
    this.valueStr = valueStr;
  }
}
