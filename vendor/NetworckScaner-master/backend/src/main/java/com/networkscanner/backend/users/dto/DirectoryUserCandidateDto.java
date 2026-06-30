package com.networkscanner.backend.users.dto;

import java.util.List;

public record DirectoryUserCandidateDto(
    String directoryDn,
    String login,
    String email,
    String displayName,
    List<String> groupDns
) {
}
