package com.networkscanner.backend.topology.model;

/**
 * Семантический тип узла топологии (иконка / легенда), не путать с {@link TopologyObjectKind#NODE}.
 */
public enum TopologyNodeKind {
  /** Сегмент сети, облако, «LAN» и т.п. */
  NETWORK,
  /** Стойка / монтажный шкаф (несколько юнитов). */
  RACK,
  /** Сервер (узел, хост). */
  SERVER,
  PRINTER,
  /** Маршрутизатор. */
  ROUTER,
  SWITCH,
  PC,
  NOTEBOOK,
  FIREWALL
}
