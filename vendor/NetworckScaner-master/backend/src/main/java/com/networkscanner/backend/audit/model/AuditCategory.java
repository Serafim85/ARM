package com.networkscanner.backend.audit.model;

public enum AuditCategory {
  MONITORING_DEVICE,
  SCAN_JOB,
  MONITORING_TEMPLATE,
  TOPOLOGY,
  DASHBOARD,
  WISLA_INTEGRATION,
  /** Попытки и ошибки аутентификации во внешнем каталоге. */
  DIRECTORY_AUTH,
  /** Вход и выход пользователей. */
  AUTH_SESSION,
  /** Управление учётными записями администратором. */
  USER_ADMIN,
  /** Настройки LDAP/AD и сопоставление групп. */
  DIRECTORY_CONFIG,
  /** SMTP и подписки на уведомления. */
  NOTIFICATION_SETTINGS,
  /** Профили доступа для сканирования. */
  ACCESS_PROFILE
}
