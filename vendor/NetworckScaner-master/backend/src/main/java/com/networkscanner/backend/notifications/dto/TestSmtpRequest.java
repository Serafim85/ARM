package com.networkscanner.backend.notifications.dto;

import jakarta.validation.constraints.Email;

public record TestSmtpRequest(
    @Email String recipientEmail,
    SmtpTestDraftRequest smtpSettings
) {
}
