package com.networkscanner.backend.dashboards.dto;

import jakarta.validation.constraints.NotBlank;

public record WidgetFieldUpsertRequest(
    @NotBlank(message = "Укажите имя поля виджета.")
    String name,
    int valueInt,
    String valueStr
) {
  public WidgetFieldUpsertRequest {
    valueStr = valueStr == null ? "" : valueStr;
  }
}
