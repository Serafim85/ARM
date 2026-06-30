package com.networkscanner.backend.network.scan.dto;

/**
 * Тип сетевого устройства, определённый по SNMP sysServices и ipForwarding.
 */
public enum SnmpDeviceType {
  ROUTER("Маршрутизатор"),
  SWITCH("Коммутатор"),
  HOST("Хост"),
  HYBRID("Гибрид");

  private final String tag;

  SnmpDeviceType(String tag) {
    this.tag = tag;
  }

  public String tag() {
    return tag;
  }
}
