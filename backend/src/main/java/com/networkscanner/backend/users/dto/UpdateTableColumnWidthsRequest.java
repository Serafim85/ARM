package com.networkscanner.backend.users.dto;

import java.util.Map;

public record UpdateTableColumnWidthsRequest(
    String tableKey,
    Map<String, Integer> widths
) {
}
