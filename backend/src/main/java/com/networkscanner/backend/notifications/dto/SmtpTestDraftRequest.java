package com.networkscanner.backend.notifications.dto;

import jakarta.validation.constraints.Email;

public record SmtpTestDraftRequest(
    Boolean enabled,
    String serverHost,
    Integer serverPort,
    Boolean auth,
    Boolean starttls,
    Boolean ssl,
    String username,
    String password,
    Boolean clearPassword,
    @Email String fromEmail
) {
}
