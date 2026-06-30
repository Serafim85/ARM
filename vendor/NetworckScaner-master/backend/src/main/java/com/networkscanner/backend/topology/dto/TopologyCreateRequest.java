package com.networkscanner.backend.topology.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networkscanner.backend.topology.model.TopologyVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;

public record TopologyCreateRequest(
    @NotBlank(message = "Укажите название топологии.")
    String name,
    @NotNull
    TopologyVisibility visibility,
    Boolean autosave,
    Boolean autoCenterOnResize,
    Set<Long> sharedUserIds,
    JsonNode document
) {
  public TopologyCreateRequest {
    sharedUserIds = sharedUserIds == null ? Set.of() : new LinkedHashSet<>(sharedUserIds);
    if (document == null || document.isNull()) {
      document = JsonNodeFactory.instance.objectNode();
    }
    autosave = Boolean.TRUE.equals(autosave);
    autoCenterOnResize = !Boolean.FALSE.equals(autoCenterOnResize);
  }
}
