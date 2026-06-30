package com.networkscanner.backend.audit.util;

/** Человекочитаемые тексты для полей аудита (подробности, IP). */
public final class AuditTextFormat {

  private AuditTextFormat() {
  }

  public static String formatIp(String raw) {
    if (raw == null || raw.isBlank() || "unknown".equalsIgnoreCase(raw.trim())) {
      return "неизвестен";
    }
    String ip = raw.trim();
    if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
      return "127.0.0.1 (локальный хост)";
    }
    return ip;
  }

  public static String authFailureDetails(String ip, String reason) {
    StringBuilder text = new StringBuilder("IP-адрес: ").append(formatIp(ip)).append(".");
    if (reason != null && !reason.isBlank()) {
      String normalized = reason.trim();
      if (!normalized.endsWith(".")) {
        normalized = normalized + ".";
      }
      text.append(" Причина: ").append(normalized);
    }
    return text.toString();
  }

  public static String profileEmailChange(String beforeEmail, String afterEmail) {
    return "Изменён email: " + beforeEmail + " → " + afterEmail + ".";
  }

  public static String profileDisplayNameChange(String beforeName, String afterName) {
    return "Изменено отображаемое имя: «" + beforeName + "» → «" + afterName + "».";
  }

  public static String profileEmailAndNameChange(
      String beforeEmail,
      String afterEmail,
      String beforeName,
      String afterName
  ) {
    return "Изменены email и отображаемое имя: "
        + beforeEmail + " → " + afterEmail
        + "; «" + beforeName + "» → «" + afterName + "».";
  }

  public static String ensureSentence(String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    String trimmed = text.trim();
    return trimmed.endsWith(".") ? trimmed : trimmed + ".";
  }
}
