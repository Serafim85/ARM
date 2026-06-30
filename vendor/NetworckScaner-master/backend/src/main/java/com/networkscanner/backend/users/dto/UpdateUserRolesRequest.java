package com.networkscanner.backend.users.dto;

import com.networkscanner.backend.users.model.RoleName;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateUserRolesRequest(
    @NotEmpty List<RoleName> roles
) {
}
