package com.networkscanner.backend.dashboards.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WidgetUpdateRequest(
    @NotNull(message = "Укажите тип виджета.")
    String widgetType,
    @NotBlank(message = "Укажите имя виджета.")
    String name,
    @Min(value = 0, message = "Позиция X не может быть отрицательной.")
    int gridX,
    @Min(value = 0, message = "Позиция Y не может быть отрицательной.")
    int gridY,
    @Min(value = 1, message = "Ширина виджета должна быть не меньше 1.")
    int width,
    @Min(value = 1, message = "Высота виджета должна быть не меньше 1.")
    int height,
    int viewMode,
    Integer refreshIntervalSeconds,
    boolean showHeader,
    Integer borderWidthPx,
    String borderColor,
    @Valid
    List<WidgetFieldUpsertRequest> fields
) {
  public WidgetUpdateRequest {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
