export type { AppRole, AuthSession } from './auth.service';

import type { AppRole } from './auth.service';

export type Availability = {
  label: string;
  active: boolean;
  tone: 'green' | 'red';
};

export type DeviceScanResult = {
  id: string;
  port?: number | null;
  hostName: string;
  name: string;
  serialNumber: string;
  ip: string;
  domainName: string;
  macAddress: string;
  vendor: string;
  model: string;
  firmwareVersion: string;
  pollingStatus: string;
  status: string;
  group: string;
  tags: string[];
  availability: Availability[];
};

export type MonitoringHealthStatus = 'NORM' | 'WARN' | 'CRITICAL';

export const MONITORING_HEALTH_STATUS_LABELS: Record<MonitoringHealthStatus, string> = {
  NORM: 'Норма',
  WARN: 'Предупреждение',
  CRITICAL: 'Критично',
};

export function monitoringHealthStatusLabel(status: MonitoringHealthStatus | null | undefined): string {
  if (!status) {
    return 'Неизвестно';
  }
  return MONITORING_HEALTH_STATUS_LABELS[status] ?? status;
}

export type MonitoringDeviceListItem = DeviceScanResult & {
  healthStatus: MonitoringHealthStatus | null;
};

export type MonitoringDevicePage = {
  content: MonitoringDeviceListItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  availableCount: number;
  unavailableCount: number;
  unknownCount: number;
};

export type MonitoringDeviceFilter = {
  q: string;
  ip: string;
  macAddress: string;
  status: string;
  tag: string;
  healthStatus: MonitoringHealthStatus | 'ALL';
  availability: MonitoringHostStatusFilter;
};

export type MonitoringMetric = {
  current: number | null;
  average: number | null;
  peak: number | null;
  /** Имя item из шаблона мониторинга (Zabbix name), если бэкенд передал */
  currentItemName?: string | null;
  averageItemName?: string | null;
  peakItemName?: string | null;
};

export type DeviceMonitoringDetails = {
  cpu: MonitoringMetric;
  ramUsedPercent: number | null;
  romUsedPercent: number | null;
  uptime: string;
  description: string;
  adminContact: string;
  hardwareVersion: string;
  location: string;
  addedAt: string;
  bootVersion: string;
  collectedAt: string | null;
  source: string;
  liveMode: boolean;
};

export type DeviceLiveTelemetryState = {
  active: boolean;
  loading: boolean;
  startedAt: string | null;
  expiresAt: string | null;
  nextRefreshAt: string | null;
  lastError: string | null;
};

export type MonitoringTab = 'overview' | 'configuration' | 'metrics' | 'events';
export type PortFilter = 'ALL' | 'ACTIVE' | 'LOGICAL';
export type MonitoringHostStatusFilter = 'ALL' | 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN';

export type DevicePortConfig = {
  name: string;
  description: string;
  adminStatus: 'UP' | 'DOWN';
  operStatus: 'UP' | 'DOWN';
  lost: 'Нет' | 'Да';
  nominalSpeed: string;
  activeSpeed: string;
  purpose: string;
  mode: string;
  kind: 'physical' | 'logical';
};

export type DevicePortConfigApi = {
  name: string;
  description: string;
  adminStatus: string;
  operStatus: string;
  lost: string;
  nominalSpeed: string;
  activeSpeed: string;
  purpose: string;
  mode: string;
  kind: string;
};

export type DeviceBackupConfig = {
  id: string;
  name: string;
  createdAt: string;
  source: string;
  size: string;
  status: 'Успешно' | 'Предупреждение';
  baselineStatus: 'Совпадает' | 'Есть отличия' | 'Эталон не задан';
  comparisonSummary: string;
  comparedAt: string | null;
};

export type BaselineConfigSummary = {
  fileName: string;
  configuredAt: string;
  source: string;
};

export type DeviceBackupSnapshot = {
  deviceIp: string;
  autoCompareSchedule: string;
  lastAutoComparisonAt: string | null;
  baseline: BaselineConfigSummary | null;
  backups: DeviceBackupConfig[];
};

export type BackupComparisonResult = {
  backupId: string;
  backupName: string;
  baselineStatus: 'Совпадает' | 'Есть отличия' | 'Эталон не задан';
  comparedAt: string;
  summary: string;
};

export type MonitoringTemplateUpdateRequest = {
  vendor?: string;
  model?: string;
  firmware?: string;
  priority: number;
};

export type MonitoringTemplateSummary = {
  id: string;
  type?: string;
  name: string;
  description: string;
  uploadedBy: string;
  uploadedByDisplayName: string;
  extendsTemplate: string | null;
  vendor: string | null;
  /** Точная модель (введённая при импорте); если null — fallback на modelRegex. */
  model: string | null;
  modelRegex: string | null;
  firmware: string | null;
  priority: number;
  schemaVersion?: string;
  packVersion?: string;
  templateVersion?: string;
  source: 'SYSTEM' | 'UPLOADED';
  deletable: boolean;
};

export type MonitoringTemplateFeatureSupport = {
  key: string;
  title: string;
  presentInTemplate: boolean;
  importSupported: boolean;
  runtimeSupported: boolean;
  apiSupported: boolean;
  uiSupported: boolean;
  notes: string;
};

export type MonitoringTemplateCoverageReport = {
  features: MonitoringTemplateFeatureSupport[];
  warnings: string[];
  blockingErrors: string[];
};

export type MonitoringTemplateItem = {
  key: string;
  name: string;
  type: string;
  valueType: string;
  units: string;
  delaySeconds: number;
  snmpOid: string | null;
  masterItemKey: string | null;
  params: string | null;
  preprocessing: string;
  valueMapName: string | null;
  discoveryPrototype: boolean;
  discoveryRuleKey: string | null;
  runtimeSupported: boolean;
};

export type MonitoringTemplateDiscoveryRule = {
  key: string;
  name: string;
  type: string;
  delaySeconds: number;
  lifetimeSeconds: number;
  hasFilter: boolean;
  itemPrototypeCount: number;
  triggerPrototypeCount: number;
  graphPrototypeCount: number;
};

export type MonitoringTemplateTrigger = {
  uuid: string | null;
  name: string;
  expression: string;
  priority: string;
  discoveryPrototype: boolean;
  discoveryRuleKey: string | null;
};

export type MonitoringTemplateValueMap = {
  name: string;
  mappings: Record<string, string>;
};

export type MonitoringTemplateDetails = {
  summary: MonitoringTemplateSummary;
  coverage: MonitoringTemplateCoverageReport;
  items: MonitoringTemplateItem[];
  discoveryRules: MonitoringTemplateDiscoveryRule[];
  triggers: MonitoringTemplateTrigger[];
  valueMaps: MonitoringTemplateValueMap[];
  graphNames: string[];
};

export type MonitoringTemplateDiffSummary = {
  replacingExistingTemplate: boolean;
  itemDelta: number;
  discoveryRuleDelta: number;
  triggerDelta: number;
  valueMapDelta: number;
  graphDelta: number;
};

export type MonitoringTemplateImportPreview = {
  details: MonitoringTemplateDetails;
  diff: MonitoringTemplateDiffSummary;
  duplicateTemplateId: boolean;
};

export type ActionResult = {
  message: string;
};

export type UserManagementRecord = {
  id: number;
  email: string;
  displayName: string;
  enabled: boolean;
  createdAt: string;
  roles: AppRole[];
};

export type UserStatusFilter = 'ALL' | 'ACTIVE' | 'BLOCKED';

export type CreateUserRequest = {
  email: string;
  displayName: string;
  password: string;
  roles: AppRole[];
  enabled: boolean;
};

export type UpdateUserProfileRequest = {
  email: string;
  displayName: string;
};

export type AdminAuditLogRecord = {
  createdAt: string;
  actor: string;
  action: string;
  target: string;
  details: string;
};

/** Персистентный журнал действий (GET /api/admin/audit/events). */
export type SystemAuditAction =
  | 'CREATE'
  | 'UPDATE'
  | 'DELETE'
  | 'LOGIN'
  | 'LOGOUT'
  | 'LOGIN_FAILED'
  | 'CONNECTION_ERROR'
  | 'INTEGRATION_PUBLISH_FAILED';

export type SystemAuditCategory =
  | 'MONITORING_DEVICE'
  | 'SCAN_JOB'
  | 'MONITORING_TEMPLATE'
  | 'TOPOLOGY'
  | 'DASHBOARD'
  | 'WISLA_INTEGRATION'
  | 'DIRECTORY_AUTH'
  | 'AUTH_SESSION'
  | 'USER_ADMIN'
  | 'DIRECTORY_CONFIG'
  | 'NOTIFICATION_SETTINGS'
  | 'ACCESS_PROFILE';

export type SystemAuditEventRecord = {
  occurredAt: string;
  actorLogin: string;
  category: SystemAuditCategory;
  action: SystemAuditAction;
  target: string;
  details: string | null;
};

export type SystemAuditEventPage = {
  content: SystemAuditEventRecord[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type DirectorySettings = {
  enabled: boolean;
  directoryType: string;
  protocol: string;
  serverHost: string;
  serverPort: number;
  baseDn: string;
  authType: string;
  bindDn: string;
  bindPassword: string;
  hasBindPassword: boolean;
  userFilter: string;
  loginAttribute: string;
  emailAttribute: string;
  displayNameAttribute: string;
  allowLocalFallback: boolean;
};

export type UpdateDirectorySettingsRequest = DirectorySettings & {
  clearBindPassword: boolean;
};

export type DirectoryGroup = {
  groupDn: string;
  groupName: string;
};

export type DirectoryRoleMapping = {
  groupDn: string;
  groupName: string;
  role: AppRole | '';
};

export type UpdateDirectoryRoleMappingsRequest = {
  items: DirectoryRoleMapping[];
};

export type DirectoryUserCandidate = {
  directoryDn: string;
  login: string;
  email: string;
  displayName: string;
  groupDns: string[];
};

export type DirectoryUserSearchRequest = {
  ldapFilter: string;
  emailAttribute: string;
  displayNameAttribute: string;
};

export type CreateUserFromDirectoryRequest = {
  directoryDn: string;
  login: string;
  email: string;
  displayName: string;
  role: AppRole;
  enabled: boolean;
};

export type SmtpSettings = {
  enabled: boolean;
  serverHost: string;
  serverPort: number;
  auth: boolean;
  starttls: boolean;
  ssl: boolean;
  username: string;
  password: string;
  hasPassword: boolean;
  fromEmail: string;
};

export type UpdateSmtpSettingsRequest = SmtpSettings & {
  clearPassword: boolean;
};

export type SmtpTestDraftRequest = {
  enabled: boolean;
  serverHost: string;
  serverPort: number;
  auth: boolean;
  starttls: boolean;
  ssl: boolean;
  username: string;
  password: string;
  clearPassword: boolean;
  fromEmail: string;
};

export type TestSmtpRequest = {
  recipientEmail: string;
  smtpSettings: SmtpTestDraftRequest;
};

export type NotificationKind = 'OPERATOR' | 'ADMIN';
export type NotificationChannel = 'SMTP';
export type NotificationSubscriptionType = 'SYSTEM' | 'DEVICE' | 'TAG_GROUP' | 'SCAN_JOB';
export type OperatorNotificationEventCode =
  | 'NEW_DEVICE_DISCOVERED'
  | 'MONITORING_EVENT_OPEN'
  | 'MONITORING_EVENT_RESOLVED'
  | 'DEVICE_UNMONITORED'
  | 'EQUIPMENT_CONFIG_CHANGED'
  | 'SCAN_JOB_SCHEDULED'
  | 'SCAN_JOB_COMPLETED'
  | 'SCAN_JOB_FAILED';
export type AdminNotificationEventCode =
  | 'USER_CREATED'
  | 'USER_LOGIN'
  | 'USER_BLOCKED'
  | 'USER_LOGIN_FAILED'
  | 'MONITORING_SETTINGS_CHANGED'
  | 'EQUIPMENT_CONFIG_CHANGED'
  | 'TEMPLATE_CHANGED'
  | 'MONITORING_TEMPLATE_APPLIED_TO_DEVICE'
  | 'DEVICE_UNMONITORED'
  | 'ADMIN_ANY';
export type NotificationEventCode = OperatorNotificationEventCode | AdminNotificationEventCode;

export type NotificationSubscription = {
  id: number | null;
  enabled: boolean;
  notificationKind: NotificationKind;
  subscriptionType: NotificationSubscriptionType;
  channel: NotificationChannel;
  eventCodes: NotificationEventCode[];
  recipientEmail: string;
  deviceIpFilter: string | null;
  deviceTagFilter: string | null;
  severityFilter: string | null;
  metricFilter: string | null;
  customCondition: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type DiscoveryMethod =
  | 'DNS' | 'FTP' | 'HTTP' | 'HTTPS' | 'ICMP' | 'IMAP' | 'LDAP' | 'NNTP' | 'POP'
  | 'SMTP' | 'SNMP_V1' | 'SNMP_V2' | 'SNMP_V3' | 'SSH' | 'TCP' | 'TELNET';

export type DiscoveryProbeConfig = {
  method: DiscoveryMethod;
  port?: number | null;
  community?: string;
  securityUsername?: string;
  authProtocol?: string;
  authPassword?: string;
  privacyProtocol?: string;
  privacyPassword?: string;
};

export type AccessProfileSummary = {
  id: number;
  name: string;
  description: string | null;
  snmpV1Enabled: boolean;
  snmpV2Enabled: boolean;
  snmpV3Enabled: boolean;
  sshEnabled: boolean;
  httpsEnabled: boolean;
};

export type AccessProfileDetail = AccessProfileSummary & {
  snmpV1Port: number | null;
  snmpV1Community: string | null;
  hasSnmpV1Community: boolean;
  snmpV2Port: number | null;
  snmpV2Community: string | null;
  hasSnmpV2Community: boolean;
  snmpV3Port: number | null;
  snmpV3SecurityUsername: string | null;
  snmpV3AuthProtocol: string | null;
  hasSnmpV3AuthPassword: boolean;
  snmpV3PrivacyProtocol: string | null;
  hasSnmpV3PrivacyPassword: boolean;
  sshPort: number | null;
  sshUsername: string | null;
  hasSshPassword: boolean;
  hasSshPrivateKey: boolean;
  hasSshPassphrase: boolean;
  httpsPort: number | null;
  httpsUsername: string | null;
  hasHttpsPassword: boolean;
  hasHttpsClientCert: boolean;
  hasHttpsClientKey: boolean;
  httpsInsecureSkipVerify: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type UpsertAccessProfileRequest = {
  name: string;
  description?: string | null;
  snmpV1Enabled: boolean;
  snmpV1Port?: number | null;
  snmpV1Community?: string | null;
  clearSnmpV1Community?: boolean;
  snmpV2Enabled: boolean;
  snmpV2Port?: number | null;
  snmpV2Community?: string | null;
  clearSnmpV2Community?: boolean;
  snmpV3Enabled: boolean;
  snmpV3Port?: number | null;
  snmpV3SecurityUsername?: string | null;
  snmpV3AuthProtocol?: string | null;
  snmpV3AuthPassword?: string | null;
  clearSnmpV3AuthPassword?: boolean;
  snmpV3PrivacyProtocol?: string | null;
  snmpV3PrivacyPassword?: string | null;
  clearSnmpV3PrivacyPassword?: boolean;
  sshEnabled: boolean;
  sshPort?: number | null;
  sshUsername?: string | null;
  sshPassword?: string | null;
  clearSshPassword?: boolean;
  sshPrivateKeyPem?: string | null;
  clearSshPrivateKey?: boolean;
  sshPassphrase?: string | null;
  clearSshPassphrase?: boolean;
  httpsEnabled: boolean;
  httpsPort?: number | null;
  httpsUsername?: string | null;
  httpsPassword?: string | null;
  clearHttpsPassword?: boolean;
  httpsClientCertPem?: string | null;
  clearHttpsClientCert?: boolean;
  httpsClientKeyPem?: string | null;
  clearHttpsClientKey?: boolean;
  httpsInsecureSkipVerify: boolean;
};

export type ScanRequestPayload = {
  subnetRange: string;
  probes: DiscoveryProbeConfig[];
  timeout: number;
  retries: number;
  accessProfileId?: number | null;
  port?: number;
  scanMode?: DiscoveryMethod;
  snmpVersion?: string;
  community?: string;
  securityUsername?: string;
  authProtocol?: string;
  authPassword?: string;
  privacyProtocol?: string;
  privacyPassword?: string;
};

export type ScanRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

export type ScanRunStartResponse = {
  runId: number;
  scanJobId: number | null;
  status: ScanRunStatus;
  totalAddresses: number;
};

export type ScanRunDto = {
  runId: number;
  source: 'MANUAL' | 'JOB';
  scanJobId: number | null;
  status: ScanRunStatus;
  totalAddresses: number;
  scannedAddresses: number;
  foundCount: number;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ScanJobStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';

export type ScanJob = {
  id: number;
  name: string;
  enabled: boolean;
  cron: string;
  lastRunAt: string | null;
  lastStatus: ScanJobStatus | null;
  lastError: string | null;
  lastResultCount: number;
  discoveredNotMonitoredCount: number;
  activeRunId: number | null;
  scannedAddresses: number;
  totalAddresses: number;
  createdAt: string;
  updatedAt: string;
};

export type MonitoringEventStatus = 'OPEN' | 'RESOLVED';

/** Значения status события мониторинга (как в API); при добавлении статуса расширить тип и подписи ниже. */
export const MONITORING_EVENT_STATUSES = ['OPEN', 'RESOLVED'] as const satisfies readonly MonitoringEventStatus[];

/** Подписи для UI по статусу события. */
export const MONITORING_EVENT_STATUS_LABELS: Record<MonitoringEventStatus, string> = {
  OPEN: 'Активно',
  RESOLVED: 'Устранено',
};

/** Severity p-tag для статуса события (PrimeNG). */
export const MONITORING_EVENT_STATUS_TAG_SEVERITY: Record<
  MonitoringEventStatus,
  'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'
> = {
  OPEN: 'danger',
  RESOLVED: 'success',
};

export function monitoringEventStatusLabel(raw: string | null | undefined): string {
  if (raw == null || raw === '') {
    return '—';
  }
  const u = String(raw).trim().toUpperCase();
  if ((MONITORING_EVENT_STATUSES as readonly string[]).includes(u)) {
    return MONITORING_EVENT_STATUS_LABELS[u as MonitoringEventStatus];
  }
  return String(raw);
}

export function monitoringEventStatusTagSeverity(
  raw: string | null | undefined
): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
  if (raw == null || raw === '') {
    return 'secondary';
  }
  const u = String(raw).trim().toUpperCase();
  if ((MONITORING_EVENT_STATUSES as readonly string[]).includes(u)) {
    return MONITORING_EVENT_STATUS_TAG_SEVERITY[u as MonitoringEventStatus];
  }
  return 'secondary';
}

/** Severity триггера Zabbix (поле thresholdLevel в API), совпадает с enum ThresholdLevel на бэкенде. */
export type MonitoringEventLevel =
  | 'NOT_CLASSIFIED'
  | 'INFORMATION'
  | 'WARNING'
  | 'AVERAGE'
  | 'HIGH'
  | 'DISASTER';

export const MONITORING_EVENT_LEVEL_LABELS: Record<MonitoringEventLevel, string> = {
  NOT_CLASSIFIED: 'Не классифицирован',
  INFORMATION: 'Информация',
  WARNING: 'Предупреждение',
  AVERAGE: 'Средний',
  HIGH: 'Высокий',
  DISASTER: 'Катастрофа',
};

/** CSS-класс для чипа уровня (без префикса event-level-). */
export function monitoringEventLevelChipClass(level: MonitoringEventLevel): string {
  const map: Record<MonitoringEventLevel, string> = {
    NOT_CLASSIFIED: 'event-level-not-classified',
    INFORMATION: 'event-level-information',
    WARNING: 'event-level-warning',
    AVERAGE: 'event-level-average',
    HIGH: 'event-level-high',
    DISASTER: 'event-level-disaster',
  };
  return map[level] ?? 'event-level-not-classified';
}

const KNOWN_LEVELS: readonly MonitoringEventLevel[] = [
  'NOT_CLASSIFIED',
  'INFORMATION',
  'WARNING',
  'AVERAGE',
  'HIGH',
  'DISASTER',
] as const;

/**
 * Приводит строку API к известному уровню (регистр, legacy WARN/CRITICAL после миграции бэка).
 */
export function normalizeMonitoringEventLevel(raw: string | null | undefined): MonitoringEventLevel {
  if (raw == null || raw === '') {
    return 'NOT_CLASSIFIED';
  }
  const u = String(raw).trim().toUpperCase();
  if (u === 'WARN') {
    return 'WARNING';
  }
  if (u === 'CRITICAL') {
    return 'HIGH';
  }
  if ((KNOWN_LEVELS as readonly string[]).includes(u)) {
    return u as MonitoringEventLevel;
  }
  return 'NOT_CLASSIFIED';
}

export function monitoringEventLevelLabel(raw: string | null | undefined): string {
  const level = normalizeMonitoringEventLevel(raw);
  return MONITORING_EVENT_LEVEL_LABELS[level];
}

export type MonitoringEvent = {
  id: number;
  deviceId: number;
  deviceIp: string;
  deviceName: string;
  /** Имя хоста из SNMP (sysName). */
  deviceHostName: string;
  deviceMacAddress: string;
  templateId: string;
  metricName: string;
  metricDisplayName?: string | null;
  triggerName: string | null;
  triggerExpression: string | null;
  recoveryExpression: string | null;
  recoveryPath: string | null;
  thresholdLevel: MonitoringEventLevel;
  thresholdValue: number;
  actualValue: number;
  breachStartedAt: string;
  normalizedAt: string | null;
  status: MonitoringEventStatus;
};

export type MonitoredDeviceMeta = {
  id: number;
  ip: string;
  snmpPort?: number | null;
  hostName: string;
  name: string;
  serialNumber: string;
  macAddress: string;
  vendor: string;
  model: string;
  firmwareVersion: string;
  pollingStatus: string;
  status: string;
  healthStatus: MonitoringHealthStatus | null;
  groupName: string;
  tags: string[];
  availability: Availability[];
  templateId: string | null;
  templateIds?: string[] | null;
  effectiveTemplateId: string | null;
  templateVersion: string | null;
  packVersion: string | null;
  schemaVersion: string | null;
  createdAt: string;
  updatedAt: string;
};

export type MetricChartThreshold = {
  metricName: string;
  instanceKey: string;
  triggerName?: string | null;
  triggerUuid?: string | null;
  thresholdLevel: string;
  thresholdValue: number;
  scaledThresholdValue?: number | null;
  operator: string;
  valueMapMappings?: Record<string, string> | null;
};

export type MonitoringItemState = {
  itemKey: string;
  itemDisplayName?: string | null;
  instanceKey: string | null;
  numericValue: number | null;
  textValue: string | null;
  unitLabel: string | null;
  scaledNumericValue?: number | null;
  scaledUnitLabel?: string | null;
  scaledDisplayValue?: string | null;
  valueMapName: string | null;
  valueMapMappings?: Record<string, string> | null;
  presentationValue: string | null;
  preprocessingStatus: string | null;
  preprocessingNote: string | null;
  lastCollectedAt: string;
  thresholds?: MetricChartThreshold[];
};

export type MonitoringItemStatePage = {
  content: MonitoringItemState[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type MonitoringDeviceItem = {
  itemUuid: string;
  itemKey: string;
  name: string;
  itemType: string;
  discoveryPrototype: boolean;
  discoveryRuleKey: string | null;
  instanceKey: string | null;
  active: boolean;
};

export type MonitoringDeviceItemSelection = {
  itemUuid: string;
  instanceKey: string | null;
};

export type MonitoringDiscoveryInstance = {
  discoveryRuleKey: string;
  instanceKey: string;
  macros: Record<string, string>;
  lastDiscoveredAt: string;
  expiresAt: string;
};

export type MonitoringEventPage = {
  content: MonitoringEvent[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type MonitoringMetricValue = {
  recordedAt: string;
  deviceIp: string;
  metricName: string;
  metricValue: number;
  unit: string;
  metricDisplayName?: string | null;
  scaledMetricValue?: number | null;
  scaledUnit?: string | null;
  scaledDisplayValue?: string | null;
};

/**
 * Компактный ряд истории метрик (batch): метаданные один раз + параллельные массивы.
 * `t` — метки времени (epoch millis), `v` — сырые значения, `sv` — масштабированные (если есть).
 */
export type MonitoringMetricsBatchSeries = {
  deviceId: number;
  metricName: string;
  displayName?: string | null;
  unit?: string | null;
  scaledUnit?: string | null;
  valueMapName?: string | null;
  valueMapMappings?: Record<string, string> | null;
  t: number[];
  v: number[];
  sv?: number[] | null;
};

export type MonitoringMetricsBatchRequest = {
  from: string | null;
  to: string | null;
  series: Array<{
    deviceId: number;
    metricName: string;
  }>;
  /** Максимум точек на ряд (децимация); опционально. */
  maxPoints?: number;
};

/** Сводка GET /api/monitoring/events/level-summary (совпадает с MonitoringEventLevelSummaryDto). */
export type MonitoringEventLevelSummary = {
  disaster: number;
  high: number;
  average: number;
  warning: number;
  information: number;
  notClassified: number;
};

export type MonitoringEventFilter = {
  status: MonitoringEventStatus | null;
  thresholdLevel?: MonitoringEventLevel | null;
  /** ID записи monitored_devices (API: deviceId). */
  deviceId?: number | null;
  breachStartedFrom: string | null;
  breachStartedTo: string | null;
  /** Интервал по normalizedAt (ISO-8601), только события с датой нормализации. */
  normalizedFrom?: string | null;
  normalizedTo?: string | null;
  minDurationSeconds: number | null;
  maxDurationSeconds: number | null;
  /** Подстрока в metricName (API: metricNameContains). */
  metricNameContains?: string | null;
  /** Подстрока в MAC устройства (API: macAddressContains). */
  macAddressContains?: string | null;
  /** Подстрока в IP устройства (API: deviceIpContains). */
  deviceIpContains?: string | null;
  /** Подстрока в имени устройства (поле name, API: deviceNameContains). */
  deviceNameContains?: string | null;
  /** Список ID устройств (API: deviceIds). */
  deviceIds?: number[] | null;
  /** Теги устройств (API: deviceTags CSV). */
  deviceTags?: string[] | null;
};

/** Ответ GET /api/dashboards/server-time */
export type ServerTimeResponse = {
  epochMillis: number;
};

export type DashboardVisibility = 'PRIVATE' | 'SHARED';

export type WidgetFieldRecord = {
  id?: number;
  name: string;
  valueInt: number;
  valueStr: string;
};

export type DashboardWidget = {
  id: number;
  dashboardId: number;
  sortOrder: number;
  name: string;
  widgetType: string;
  gridX: number;
  gridY: number;
  width: number;
  height: number;
  viewMode: number;
  refreshIntervalSeconds: number | null;
  showHeader: boolean;
  /** Толщина рамки ячейки gridster (px), по умолчанию 1. */
  borderWidthPx?: number;
  /** CSS-цвет рамки (имя, #hex, rgb и т.д.), по умолчанию gray. */
  borderColor?: string;
  fields: WidgetFieldRecord[];
};

export type DashboardRecord = {
  id: number;
  ownerId: number;
  name: string;
  visibility: DashboardVisibility;
  sharedUserIds: number[];
  createdAt: string;
  updatedAt: string;
  widgets: DashboardWidget[];
};

export type DashboardCreateRequest = {
  name: string;
  visibility: DashboardVisibility;
  sharedUserIds: number[];
};

export type DashboardUpdateRequest = DashboardCreateRequest;

export type WidgetFieldUpsert = {
  name: string;
  valueInt: number;
  valueStr: string;
};

export type WidgetCreatePayload = {
  widgetType: string;
  name: string;
  gridX: number;
  gridY: number;
  width: number;
  height: number;
  viewMode: number;
  refreshIntervalSeconds: number | null;
  showHeader: boolean;
  borderWidthPx: number;
  borderColor: string;
  fields: WidgetFieldUpsert[];
};

export type WidgetUpdatePayload = WidgetCreatePayload;

export type UserDirectoryEntry = {
  id: number;
  email: string;
  displayName: string;
};

export type TopologyVisibility = 'PRIVATE' | 'SHARED';

export type TopologyObjectKind = 'NODE' | 'EDGE' | 'GROUP';

export type TopologyNodeKind =
  | 'NETWORK'
  | 'RACK'
  | 'SERVER'
  | 'PRINTER'
  | 'ROUTER'
  | 'SWITCH'
  | 'PC'
  | 'NOTEBOOK'
  | 'FIREWALL';

/** Агрегат доступности устройства в мониторинге для узла топологии (ответ API). */
export type TopologyDeviceHostAvailability = 'AVAILABLE' | 'UNAVAILABLE' | 'UNKNOWN';

export type TopologyRecord = {
  id: number;
  ownerId: number;
  name: string;
  visibility: TopologyVisibility;
  autosave: boolean;
  /** Подгонка вида (fit) при изменении размера области графа; по умолчанию true (если поле отсутствует). */
  autoCenterOnResize?: boolean;
  sharedUserIds: number[];
  createdAt: string;
  updatedAt: string;
  document: unknown;
  /** Подложка корневого уровня (нет родителя-слоя в topology_objects). */
  rootLayerBackdropColor?: string | null;
};

export type TopologyCreateRequest = {
  name: string;
  visibility: TopologyVisibility;
  autosave?: boolean;
  autoCenterOnResize?: boolean;
  sharedUserIds: number[];
  document?: unknown;
};

export type TopologyUpdateRequest = {
  name: string;
  visibility: TopologyVisibility;
  autosave?: boolean;
  autoCenterOnResize?: boolean;
  sharedUserIds: number[];
  document?: unknown;
  /** Пустая строка — сброс цвета подложки корневого слоя; не передавать — без изменений. */
  rootLayerBackdropColor?: string | null;
};

export type TopologyObjectRecord = {
  id: number;
  kind: TopologyObjectKind;
  topologyId: number;
  elementId: string;
  name: string | null;
  status: string | null;
  description: string | null;
  layerId: number | null;
  groupId: number | null;
  positionX: number | null;
  positionY: number | null;
  nodeKind: TopologyNodeKind | null;
  deviceId: number | null;
  /** NODE + deviceId: доступность по мониторингу (поле status устройства). */
  deviceHostAvailability?: TopologyDeviceHostAvailability | null;
  /** NODE + deviceId: состояние health_status устройства (NORM / WARN / CRITICAL). */
  deviceHealthStatus?: MonitoringHealthStatus | null;
  sourceObjectId: number | null;
  targetObjectId: number | null;
  sourceElementId: string | null;
  targetElementId: string | null;
  /** Только kind === GROUP: размеры прямоугольной области. */
  frameWidth?: number | null;
  frameHeight?: number | null;
  /** Только GROUP: цвет рамки (#RRGGBB); null — цвет по умолчанию в UI. */
  frameBorderColor?: string | null;
  /** Только GROUP: загружен ли фон слоя (PNG/JPEG/SVG). */
  layerBackgroundPresent?: boolean | null;
  /** Только EDGE: цвет линии и стрелки (#RRGGBB); null — по умолчанию в UI. */
  lineColor?: string | null;
  /** NODE и GROUP: цвет подложки слоя (#RRGGBB); null — без заливки. */
  layerBackdropColor?: string | null;
};

export type TopologyObjectCreatePayload = {
  kind: TopologyObjectKind;
  elementId?: string | null;
  name?: string | null;
  status?: string | null;
  description?: string | null;
  layerId?: number | null;
  groupId?: number | null;
  positionX?: number | null;
  positionY?: number | null;
  nodeKind?: TopologyNodeKind | null;
  deviceId?: number | null;
  sourceObjectId?: number | null;
  targetObjectId?: number | null;
  frameWidth?: number | null;
  frameHeight?: number | null;
  /** Только GROUP: #RRGGBB или #RGB. */
  frameBorderColor?: string | null;
  /** Только EDGE: #RRGGBB или #RGB. */
  lineColor?: string | null;
  /**
   * Только на клиенте при flush: elementId несохранённой группы-родителя (не отправлять в API как есть).
   */
  parentElementId?: string;
};

/** Элемент пакетного сохранения раскладки (см. PUT …/objects/layout-batch). */
export type TopologyLayoutPatchItem = {
  objectId: number;
  positionX?: number | null;
  positionY?: number | null;
  frameWidth?: number | null;
  frameHeight?: number | null;
};

export type TopologyLayoutBatchUpdatePayload = {
  items: TopologyLayoutPatchItem[];
};

/** Частичное обновление объекта (PUT): NODE — центр; GROUP — центр и/или размеры рамки. */
export type TopologyObjectUpdatePayload = {
  positionX?: number | null;
  positionY?: number | null;
  name?: string | null;
  frameWidth?: number | null;
  frameHeight?: number | null;
  /** Только GROUP: задать цвет рамки; пустая строка — сброс к значению по умолчанию. */
  frameBorderColor?: string | null;
  /** NODE и GROUP: цвет подложки слоя; пустая строка — сброс. */
  layerBackdropColor?: string | null;
  /** Только EDGE: цвет линии; пустая строка — сброс. */
  lineColor?: string | null;
  groupId?: number | null;
  /** Снять объект с группы (не передавать вместе с groupId). */
  clearGroup?: boolean | null;
  /** Только NODE. */
  nodeKind?: TopologyNodeKind | null;
  /** Только NODE: привязать устройство мониторинга (не сочетать с clearDevice). */
  deviceId?: number | null;
  /** Только NODE: снять привязку к устройству. */
  clearDevice?: boolean | null;
};
