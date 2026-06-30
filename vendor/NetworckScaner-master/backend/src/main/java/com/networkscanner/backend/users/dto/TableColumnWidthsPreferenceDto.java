package com.networkscanner.backend.users.dto;

import java.util.Map;

public record TableColumnWidthsPreferenceDto(
    Map<String, Map<String, Integer>> widths
) {
}
