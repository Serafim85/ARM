package com.networkscanner.backend.notifications.dto;

public record SmtpSettingsDto(
    boolean enabled,
    String serverHost,
    Integer serverPort,
    boolean auth,
    boolean starttls,
    boolean ssl,
    String username,
    String password,
    boolean hasPassword,
    String fromEmail
) {
}
