package com.networkscanner.backend.topology.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Пакетное применение координат узлов и рамок групп в одной транзакции (без «рваной» раскладки после F5).
 */
public record TopologyLayoutBatchUpdateRequest(
    @NotEmpty @Valid List<TopologyLayoutPatchItem> items
) {}
