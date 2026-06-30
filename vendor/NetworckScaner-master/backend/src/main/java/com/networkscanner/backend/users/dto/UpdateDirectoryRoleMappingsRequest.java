package com.networkscanner.backend.users.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateDirectoryRoleMappingsRequest(
    @NotNull @Valid List<DirectoryRoleMappingDto> items
) {
}
