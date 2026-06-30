import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { TableModule } from 'primeng/table';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { TabsModule } from 'primeng/tabs';
import { TooltipModule } from 'primeng/tooltip';
import { monitoringHealthStatusLabel } from '../../models';
import type {
  AppRole,
  CreateUserFromDirectoryRequest,
  DirectoryGroup,
  DirectoryRoleMapping,
  DirectorySettings,
  DirectoryUserCandidate,
  MonitoringDeviceListItem,
  MonitoringDevicePage,
  MonitoringDeviceItem,
  MonitoringHealthStatus,
  MonitoringHostStatusFilter,
  NotificationEventCode,
  NotificationSubscriptionType,
  NotificationSubscription,
  SmtpSettings
} from '../../models';
import { API_BASE_URL } from '../../api-config';
import { NotifierService } from '../../notifier.service';
import { AuthService } from '../../auth.service';
import { SystemSettingsService } from '../../services/system-settings.service';
import { AccessProfilesTabComponent } from './access-profiles-tab.component';

@Component({
  selector: 'app-system-settings-page',
  standalone: true,
  imports: [
    FormsModule,
    TabsModule,
    InputTextModule,
    PasswordModule,
    CheckboxModule,
    ButtonModule,
    SelectModule,
    MultiSelectModule,
    DialogModule,
    TableModule,
    TooltipModule,
    AccessProfilesTabComponent,
  ],
  templateUrl: './system-settings-page.component.html',
  styleUrl: './system-settings-page.component.css',
})
export class SystemSettingsPageComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly settingsService = inject(SystemSettingsService);
  private readonly notify = inject(NotifierService);
  private readonly auth = inject(AuthService);

  protected readonly directoryFieldHints = {
    directoryType:
      'Выберите тип вашего LDAP-сервера: вариант LDAP подходит для OpenLDAP, 389 DS, а Microsoft Active Directory — для AD. От этого выбора зависит рекомендуемый набор атрибутов.',
    protocol:
      'Определите способ подключения: LDAP (обычный, порт 389), LDAPS (защищённый через SSL/TLS, порт 636) или STARTTLS (защита поверх обычного соединения).',
    serverHost:
      'Укажите IP-адрес или полное доменное имя контроллера домена, например: dc01.company.local или 192.168.1.10.',
    serverPort:
      'Номер порта для подключения. Стандартные значения: 389 для LDAP, 636 для LDAPS. Меняйте только если администратор изменил порт вручную.',
    baseDn:
      'Корневая ветка каталога, где будет выполняться поиск пользователей. Пример: DC=company,DC=local. Все входящие пользователи должны находиться внутри этой ветки.',
    authType:
      'Метод проверки подлинности. SIMPLE — обычный логин и пароль (используется чаще всего). ANONYMOUS — анонимное подключение без пароля (требует специальной настройки сервера).',
    bindDn:
      'Учётная запись для подключения системы к LDAP. Пример: CN=svc-ldap,OU=Service,DC=company,DC=local. Эта запись должна иметь права на чтение пользователей и групп.',
    bindPassword:
      'Пароль от учётной записи bind DN. Оставьте поле пустым, если не хотите менять уже сохранённый пароль. При сохранении пароль не отображается в открытом виде.',
    clearBindPassword:
      'Отметьте этот флажок, если нужно удалить ранее сохранённый пароль bind DN. Полезно при смене пароля учётной записи или перезапуске настройки.',
    userFilter:
      'LDAP-запрос, который находит пользователя по логину или email. По умолчанию: (|(uid={login})(mail={login})(sAMAccountName={login})). Часть {login} заменяется на то, что пользователь ввёл в поле логина.',
    loginAttribute:
      'Название поля в LDAP, где хранится логин пользователя. Для Active Directory обычно sAMAccountName, для OpenLDAP — uid. Именно это поле сравнивается с введённым логином.',
    emailAttribute:
      'Атрибут, содержащий email пользователя. Стандартное значение — mail. Используется для уведомлений и привязки профиля.',
    displayNameAttribute:
      'Поле, в котором хранится отображаемое имя пользователя (ФИО). Чаще всего используется cn (commonName), в AD также может быть displayName.',
    enabled:
      'Разрешить пользователям входить через внешний LDAP-сервер. При выключении работают только локальные учётные записи. После включения проверка логина и пароля идёт через LDAP.',
    allowLocalFallback:
      'Если LDAP-сервер не отвечает (сетевые проблемы, отказ, таймаут), система попробует локальную аутентификацию. Для этого у пользователя должен быть предварительно установлен локальный пароль. Полезно для аварийного доступа.',
  } as const;

  protected readonly loading = signal(false);
  protected readonly monitoringHealthStatusLabel = monitoringHealthStatusLabel;
  protected readonly saving = signal(false);
  protected readonly loadingGroups = signal(false);
  protected readonly savingMappings = signal(false);
  protected readonly activeTab = signal<
    'directory' | 'roleMapping' | 'userCreate' | 'smtp' | 'accessProfiles' | 'subscriptions'
  >('accessProfiles');
  private readonly accessProfilesTab = viewChild(AccessProfilesTabComponent);
  protected readonly isAdmin = signal(false);
  protected readonly debugMode = signal(false);
  protected readonly clearBindPassword = signal(false);
  protected readonly mappingRows = signal<DirectoryRoleMapping[]>([]);
  protected readonly discoveredGroups = signal<DirectoryGroup[]>([]);
  protected readonly directoryUserFilter = signal('(&(objectClass=inetOrgPerson)(uid={login}))');
  protected readonly createEmailAttribute = signal('mail');
  protected readonly createDisplayNameAttribute = signal('displayName');
  protected readonly foundDirectoryUsers = signal<DirectoryUserCandidate[]>([]);
  protected readonly selectedDirectoryDn = signal<string | null>(null);
  protected readonly createDirectoryRole = signal<AppRole>('VIEWER');
  protected readonly createDirectoryUserEnabled = signal(true);
  protected readonly smtpSaving = signal(false);
  protected readonly smtpTesting = signal(false);
  protected readonly clearSmtpPassword = signal(false);
  protected readonly testSmtpRecipient = signal('');
  protected readonly smtpForm = signal<SmtpSettings>({
    enabled: false,
    serverHost: '',
    serverPort: 25,
    auth: false,
    starttls: false,
    ssl: false,
    username: '',
    password: '',
    hasPassword: false,
    fromEmail: '',
  });
  protected readonly subscriptionRows = signal<NotificationSubscription[]>([]);
  protected readonly subscriptionsLoading = signal(false);
  protected readonly subscriptionsSaving = signal(false);
  protected readonly testEventDialogOpen = signal(false);
  protected readonly testEventSending = signal(false);
  protected readonly testEventKind = signal<'ADMIN' | 'OPERATOR'>(this.isAdmin() ? 'ADMIN' : 'OPERATOR');
  protected readonly testEventCode = signal('MONITORING_EVENT_OPEN');
  protected readonly testEventDeviceIp = signal('127.0.0.1');
  protected readonly testEventDeviceName = signal('TEST_DEVICE');
  protected readonly testEventSeverity = signal('HIGH');
  protected readonly testEventMetricName = signal('test.metric');
  protected readonly testEventDeviceTags = signal('core');
  protected readonly testEventDetails = signal('Тестовое событие из интерфейса');
  protected readonly subscriptionWizardOpen = signal(false);
  protected readonly subscriptionWizardStep = signal(1);
  protected readonly wizardNotificationKind = signal<'ADMIN' | 'OPERATOR'>('OPERATOR');
  protected readonly wizardEditingSubscriptionId = signal<number | null>(null);
  protected readonly wizardBaseSubscription = signal<NotificationSubscription | null>(null);
  protected readonly wizardBaseCustomCondition = signal('');
  protected readonly wizardAdminEventCodes = signal<NotificationEventCode[]>([]);
  protected readonly wizardDevicesLoading = signal(false);
  protected readonly wizardDevices = signal<MonitoringDeviceListItem[]>([]);
  protected readonly wizardSelectedDeviceIds = signal<string[]>([]);
  protected readonly wizardMetricsLoading = signal(false);
  protected readonly wizardMetricSearch = signal('');
  protected readonly wizardMetricOptions = signal<Array<{
    id: string;
    label: string;
    deviceId: string;
    deviceName: string;
    metricName: string;
    itemUuid: string;
    instanceKey: string | null;
  }>>([]);
  protected readonly wizardSelectedMetricIds = signal<string[]>([]);
  protected readonly wizardRecipientEmail = signal('');
  protected readonly wizardSeveritySelection = signal<string[]>([]);
  protected readonly wizardNotifyDeviceUnmonitored = signal(true);
  protected readonly wizardNotifyConfigChanged = signal(true);
  protected readonly wizardConditionRows = signal<Array<{
    field: 'event' | 'severity' | 'metric' | 'ip' | 'tag';
    value: string;
    joinWithNext: '&&' | '||';
    negated: boolean;
  }>>([]);
  protected readonly wizardFilteredMetricOptions = computed(() => {
    const query = this.wizardMetricSearch().trim().toLowerCase();
    const options = this.wizardMetricOptions();
    if (!query) {
      return options;
    }
    return options.filter((m) =>
      [m.deviceName, m.metricName, m.deviceId, m.itemUuid, m.label]
        .join(' ')
        .toLowerCase()
        .includes(query)
    );
  });
  protected readonly wizardAvailabilityDraft = signal<MonitoringHostStatusFilter>('ALL');
  protected readonly wizardSearchDraft = signal('');
  protected readonly wizardIpDraft = signal('');
  protected readonly wizardMacDraft = signal('');
  protected readonly wizardStatusDraft = signal('');
  protected readonly wizardTagDraft = signal<string[]>([]);
  protected readonly wizardTagInputDraft = signal('');
  protected readonly wizardHealthDraft = signal<MonitoringHealthStatus | 'ALL'>('ALL');
  protected readonly severityFilterSelection = signal<string[]>([]);
  protected readonly subscriptionDraft = signal<NotificationSubscription>({
    id: null,
    enabled: true,
    notificationKind: 'OPERATOR',
    subscriptionType: 'DEVICE',
    channel: 'SMTP',
    eventCodes: ['NEW_DEVICE_DISCOVERED'],
    recipientEmail: '',
    deviceIpFilter: null,
    deviceTagFilter: null,
    severityFilter: null,
    metricFilter: null,
    customCondition: null,
  });
  protected readonly operatorEventOptions: Array<{ label: string; value: NotificationEventCode }> = [
    { label: 'Обнаружено новое устройство', value: 'NEW_DEVICE_DISCOVERED' },
    { label: 'Сработало событие мониторинга', value: 'MONITORING_EVENT_OPEN' },
    { label: 'Событие мониторинга устранено', value: 'MONITORING_EVENT_RESOLVED' },
    { label: 'Устройство снято с мониторинга', value: 'DEVICE_UNMONITORED' },
    { label: 'Изменен конфигурационный файл устройства', value: 'EQUIPMENT_CONFIG_CHANGED' },
  ];
  protected readonly scanJobEventOptions: Array<{ label: string; value: NotificationEventCode }> = [
    { label: 'Запланированное сканирование запущено', value: 'SCAN_JOB_SCHEDULED' },
    { label: 'Сканирование завершено', value: 'SCAN_JOB_COMPLETED' },
    { label: 'Ошибка сканирования', value: 'SCAN_JOB_FAILED' },
  ];
  protected readonly operatorSubscriptionTypeOptions: Array<{ label: string; value: NotificationSubscriptionType }> = [
    { label: 'Подписка на устройство', value: 'DEVICE' },
    { label: 'Подписка на группу устройств (по тегу)', value: 'TAG_GROUP' },
    { label: 'Подписка на работы по сканированию', value: 'SCAN_JOB' },
  ];
  protected readonly adminSubscriptionTypeOptions: Array<{ label: string; value: NotificationSubscriptionType }> = [
    { label: 'Системные события', value: 'SYSTEM' },
  ];
  protected readonly adminEventOptions: Array<{ label: string; value: NotificationEventCode }> = [
    { label: 'Создан новый пользователь', value: 'USER_CREATED' },
    { label: 'Авторизация пользователя', value: 'USER_LOGIN' },
    { label: 'Блокировка пользователя', value: 'USER_BLOCKED' },
    { label: 'Неудачная попытка входа', value: 'USER_LOGIN_FAILED' },
    { label: 'Изменены настройки мониторинга', value: 'MONITORING_SETTINGS_CHANGED' },
    { label: 'Изменена конфигурация оборудования', value: 'EQUIPMENT_CONFIG_CHANGED' },
    { label: 'Изменен активный шаблон', value: 'TEMPLATE_CHANGED' },
    { label: 'Применен иной шаблон к устройству', value: 'MONITORING_TEMPLATE_APPLIED_TO_DEVICE' },
    { label: 'Устройство снято с мониторинга', value: 'DEVICE_UNMONITORED' },
  ];
  protected readonly severityFilterOptions: Array<{ label: string; value: string }> = [
    { label: 'Не классифицировано', value: 'NOT_CLASSIFIED' },
    { label: 'Информация', value: 'INFORMATION' },
    { label: 'Предупреждение', value: 'WARNING' },
    { label: 'Средний', value: 'AVERAGE' },
    { label: 'Высокий', value: 'HIGH' },
    { label: 'Катастрофа', value: 'DISASTER' },
  ];
  protected readonly healthStatusOptions = [
    { label: 'Все', value: 'ALL' as const },
    { label: 'Норма', value: 'NORM' as const },
    { label: 'Предупреждение', value: 'WARN' as const },
    { label: 'Критично', value: 'CRITICAL' as const },
  ];
  protected readonly availabilityOptions = [
    { label: 'Все', value: 'ALL' as const },
    { label: 'Доступен', value: 'AVAILABLE' as const },
    { label: 'Недоступен', value: 'UNAVAILABLE' as const },
    { label: 'Неизвестно', value: 'UNKNOWN' as const },
  ];
  protected readonly conditionFieldOptions: Array<{ label: string; value: 'event' | 'severity' | 'metric' | 'ip' | 'tag' }> = [
    { label: 'Событие', value: 'event' },
    { label: 'Критичность', value: 'severity' },
    { label: 'Метрика', value: 'metric' },
    { label: 'IP устройства', value: 'ip' },
    { label: 'Тег устройства', value: 'tag' },
  ];
  private readonly severityLabelByCode: Record<string, string> = {
    NOT_CLASSIFIED: 'Не классифицировано',
    INFORMATION: 'Информация',
    WARNING: 'Предупреждение',
    AVERAGE: 'Средний',
    HIGH: 'Высокий',
    DISASTER: 'Катастрофа',
  };
  private readonly eventLabelByCode: Record<string, string> = {
    NEW_DEVICE_DISCOVERED: 'Обнаружено новое устройство',
    MONITORING_EVENT_OPEN: 'Сработало событие мониторинга',
    MONITORING_EVENT_RESOLVED: 'Событие мониторинга устранено',
    DEVICE_UNMONITORED: 'Устройство снято с мониторинга',
    EQUIPMENT_CONFIG_CHANGED: 'Изменен конфигурационный файл устройства',
    SCAN_JOB_SCHEDULED: 'Запланированное сканирование запущено',
    SCAN_JOB_COMPLETED: 'Сканирование завершено',
    SCAN_JOB_FAILED: 'Ошибка сканирования',
    ADMIN_ANY: 'Любое административное событие',
    USER_CREATED: 'Создан новый пользователь',
    USER_LOGIN: 'Авторизация пользователя',
    USER_BLOCKED: 'Блокировка пользователя',
    USER_LOGIN_FAILED: 'Неудачная попытка входа',
    MONITORING_SETTINGS_CHANGED: 'Изменены настройки мониторинга',
    TEMPLATE_CHANGED: 'Изменен активный шаблон',
    MONITORING_TEMPLATE_APPLIED_TO_DEVICE: 'Применен иной шаблон к устройству',
  };
  protected readonly subscriptionKindOptions = signal<Array<{ label: string; value: 'ADMIN' | 'OPERATOR' }>>([]);
  protected readonly testOperatorEventOptions: Array<{ label: string; value: string }> = [
    { label: 'Сработало событие мониторинга', value: 'MONITORING_EVENT_OPEN' },
    { label: 'Событие мониторинга устранено', value: 'MONITORING_EVENT_RESOLVED' },
    { label: 'Устройство снято с мониторинга', value: 'DEVICE_UNMONITORED' },
    { label: 'Изменен конфигурационный файл устройства', value: 'EQUIPMENT_CONFIG_CHANGED' },
    { label: 'Запланированное сканирование запущено', value: 'SCAN_JOB_SCHEDULED' },
    { label: 'Сканирование завершено', value: 'SCAN_JOB_COMPLETED' },
    { label: 'Ошибка сканирования', value: 'SCAN_JOB_FAILED' },
  ];
  protected readonly testAdminEventOptions: Array<{ label: string; value: string }> = [
    { label: 'Любое административное событие', value: 'ADMIN_ANY' },
    { label: 'Создан новый пользователь', value: 'USER_CREATED' },
    { label: 'Авторизация пользователя', value: 'USER_LOGIN' },
    { label: 'Блокировка пользователя', value: 'USER_BLOCKED' },
    { label: 'Неудачная попытка входа', value: 'USER_LOGIN_FAILED' },
  ];
  protected readonly roleOptions: Array<{ label: string; value: AppRole | '' }> = [
    { label: 'Не назначать', value: '' },
    { label: 'Администратор', value: 'ADMIN' },
    { label: 'Оператор', value: 'OPERATOR' },
    { label: 'Наблюдатель', value: 'VIEWER' },
  ];
  protected readonly createRoleOptions: Array<{ label: string; value: AppRole }> = [
    { label: 'Администратор', value: 'ADMIN' },
    { label: 'Оператор', value: 'OPERATOR' },
    { label: 'Наблюдатель', value: 'VIEWER' },
  ];
  protected readonly form = signal<DirectorySettings>({
    enabled: false,
    directoryType: 'LDAP',
    protocol: 'LDAP',
    serverHost: '',
    serverPort: 389,
    baseDn: '',
    authType: 'SIMPLE',
    bindDn: '',
    bindPassword: '',
    hasBindPassword: false,
    userFilter: '(|(uid={login})(mail={login})(sAMAccountName={login}))',
    loginAttribute: 'uid',
    emailAttribute: 'mail',
    displayNameAttribute: 'cn',
    allowLocalFallback: true,
  });

  ngOnInit(): void {
    this.settingsService.getAppConfig().subscribe({
      next: (config) => this.debugMode.set(!!config?.debugMode),
      error: () => this.debugMode.set(false),
    });
    this.isAdmin.set((this.auth.authSession()?.roles ?? []).includes('ADMIN'));
    const isOperator = (this.auth.authSession()?.roles ?? []).includes('OPERATOR');
    const kind: 'ADMIN' | 'OPERATOR' = this.isAdmin() ? 'ADMIN' : 'OPERATOR';
    this.subscriptionKindOptions.set(
      this.isAdmin()
        ? [{ label: 'Административное', value: 'ADMIN' }]
        : isOperator
          ? [{ label: 'Операторское', value: 'OPERATOR' }]
          : []
    );
    this.subscriptionDraft.update((v) => ({
      ...v,
      notificationKind: kind,
      subscriptionType: kind === 'ADMIN' ? 'SYSTEM' : 'DEVICE',
      eventCodes: [kind === 'ADMIN' ? 'ADMIN_ANY' : 'NEW_DEVICE_DISCOVERED'],
    }));
    this.severityFilterSelection.set([]);
    if (this.isAdmin()) {
      this.activeTab.set('accessProfiles');
      this.loadDirectorySettings();
      this.loadRoleMappings();
    } else {
      this.activeTab.set('subscriptions');
    }
    if (this.isAdmin()) {
      this.loadSmtp();
    }
    this.loadSubscriptions();
  }

  protected refreshActiveTab(): void {
    switch (this.activeTab()) {
      case 'accessProfiles':
        this.accessProfilesTab()?.load();
        return;
      case 'directory':
        this.loadDirectorySettings();
        return;
      case 'roleMapping':
        this.loadRoleMappings();
        return;
      case 'smtp':
        this.loadSmtp();
        return;
      case 'subscriptions':
        this.loadSubscriptions();
        return;
      default:
        return;
    }
  }

  protected loadDirectorySettings(): void {
    this.loading.set(true);
    this.settingsService.getDirectorySettings().subscribe({
      next: (settings) => {
        this.form.set({ ...settings, bindPassword: '' });
        this.directoryUserFilter.set(settings.userFilter || '(&(objectClass=inetOrgPerson)(uid={login}))');
        this.createEmailAttribute.set(settings.emailAttribute || 'mail');
        this.createDisplayNameAttribute.set(settings.displayNameAttribute || 'displayName');
        this.clearBindPassword.set(false);
        this.loading.set(false);
      },
      error: () => {
        this.notify.error('Не удалось загрузить системные настройки каталога.', 'Настройка системы');
        this.loading.set(false);
      },
    });
  }

  protected save(): void {
    const current = this.form();
    if (!current.serverHost.trim() || !current.baseDn.trim() || !current.userFilter.trim()) {
      this.notify.warn('Заполните сервер, Base DN и LDAP-фильтр.', 'Настройка системы');
      return;
    }
    this.saving.set(true);
    this.settingsService
      .updateDirectorySettings({
        ...current,
        clearBindPassword: this.clearBindPassword(),
      })
      .subscribe({
        next: (saved) => {
          this.form.set({ ...saved, bindPassword: '' });
          this.clearBindPassword.set(false);
          this.notify.success('Настройки интеграции с каталогом сохранены.', 'Настройка системы');
          this.saving.set(false);
        },
        error: (error) => {
          const message =
            error?.error?.message && typeof error.error.message === 'string'
              ? error.error.message
              : 'Не удалось сохранить настройки каталога.';
          this.notify.error(message, 'Настройка системы');
          this.saving.set(false);
        },
      });
  }

  protected updateField<K extends keyof DirectorySettings>(key: K, value: DirectorySettings[K]): void {
    this.form.update((v) => ({ ...v, [key]: value }));
  }

  protected loadSmtp(): void {
    this.settingsService.getSmtpSettings().subscribe({
      next: (settings) => {
        this.smtpForm.set({ ...settings, password: '' });
        this.testSmtpRecipient.set(settings.fromEmail ?? '');
        this.clearSmtpPassword.set(false);
      },
      error: () => {
        this.notify.error('Не удалось загрузить настройки SMTP.', 'Почтовый сервер');
      },
    });
  }

  protected saveSmtp(): void {
    const current = this.smtpForm();
    if (!current.serverHost.trim() || !current.fromEmail.trim()) {
      this.notify.warn('Заполните SMTP-сервер и email отправителя.', 'Почтовый сервер');
      return;
    }
    this.smtpSaving.set(true);
    this.settingsService.updateSmtpSettings({
      ...current,
      clearPassword: this.clearSmtpPassword(),
    }).subscribe({
      next: (saved) => {
        this.smtpForm.set({ ...saved, password: '' });
        this.clearSmtpPassword.set(false);
        this.notify.success('Настройки SMTP сохранены.', 'Почтовый сервер');
        this.smtpSaving.set(false);
      },
      error: () => {
        this.notify.error('Не удалось сохранить настройки SMTP.', 'Почтовый сервер');
        this.smtpSaving.set(false);
      },
    });
  }

  protected sendTestSmtpEmail(): void {
    const recipient = this.testSmtpRecipient().trim();
    if (!recipient) {
      this.notify.warn('Укажите email для тестового письма.', 'Почтовый сервер');
      return;
    }
    const smtp = this.smtpForm();
    this.smtpTesting.set(true);
    this.settingsService.sendTestSmtpEmail({
      recipientEmail: recipient,
      smtpSettings: {
        enabled: smtp.enabled,
        serverHost: smtp.serverHost,
        serverPort: smtp.serverPort,
        auth: smtp.auth,
        starttls: smtp.starttls,
        ssl: smtp.ssl,
        username: smtp.username,
        password: smtp.password,
        clearPassword: this.clearSmtpPassword(),
        fromEmail: smtp.fromEmail,
      },
    }).subscribe({
      next: () => {
        this.notify.success('Тестовое письмо отправлено.', 'Почтовый сервер');
        this.smtpTesting.set(false);
      },
      error: (error) => {
        const message = this.extractHttpErrorMessage(error, 'Не удалось отправить тестовое письмо.');
        this.notify.error(message, 'Почтовый сервер');
        this.smtpTesting.set(false);
      },
    });
  }

  protected updateSmtpField<K extends keyof SmtpSettings>(key: K, value: SmtpSettings[K]): void {
    this.smtpForm.update((v) => ({ ...v, [key]: value }));
  }

  protected loadSubscriptions(): void {
    this.subscriptionsLoading.set(true);
    this.settingsService.listNotificationSubscriptions().subscribe({
      next: (rows) => {
        this.subscriptionRows.set(rows);
        this.subscriptionsLoading.set(false);
      },
      error: () => {
        this.subscriptionRows.set([]);
        this.subscriptionsLoading.set(false);
      },
    });
  }

  protected saveSubscriptionDraft(): void {
    const draft = this.subscriptionDraft();
    if (!draft.recipientEmail.trim()) {
      this.notify.warn('Укажите email получателя.', 'Подписки уведомлений');
      return;
    }
    this.subscriptionsSaving.set(true);
    this.settingsService.upsertNotificationSubscription(draft).subscribe({
      next: () => {
        this.notify.success('Подписка сохранена.', 'Подписки уведомлений');
        this.subscriptionsSaving.set(false);
        this.subscriptionDraft.set({
          id: null,
          enabled: true,
          notificationKind: this.preferredNotificationKind(),
          subscriptionType: this.preferredNotificationKind() === 'ADMIN' ? 'SYSTEM' : 'DEVICE',
          channel: 'SMTP',
          eventCodes: [this.preferredNotificationKind() === 'ADMIN' ? 'ADMIN_ANY' : 'NEW_DEVICE_DISCOVERED'],
          recipientEmail: '',
          deviceIpFilter: null,
          deviceTagFilter: null,
          severityFilter: null,
          metricFilter: null,
          customCondition: null,
        });
        this.severityFilterSelection.set([]);
        this.loadSubscriptions();
      },
      error: () => {
        this.notify.error('Не удалось сохранить подписку.', 'Подписки уведомлений');
        this.subscriptionsSaving.set(false);
      },
    });
  }

  protected editSubscription(row: NotificationSubscription): void {
    const kind: 'ADMIN' | 'OPERATOR' = row.notificationKind === 'ADMIN' ? 'ADMIN' : 'OPERATOR';
    this.wizardNotificationKind.set(kind);
    this.wizardEditingSubscriptionId.set(row.id ?? null);
    this.wizardBaseSubscription.set(row);
    this.wizardBaseCustomCondition.set((row.customCondition ?? '').trim());
    this.wizardRecipientEmail.set(row.recipientEmail ?? this.auth.authSession()?.email ?? '');
    this.wizardAdminEventCodes.set((row.eventCodes ?? []).filter((v): v is NotificationEventCode => !!v));
    this.wizardSeveritySelection.set(this.parseSeverityFilter(row.severityFilter));
    const codes = row.eventCodes ?? [];
    this.wizardNotifyDeviceUnmonitored.set(codes.includes('DEVICE_UNMONITORED'));
    this.wizardNotifyConfigChanged.set(codes.includes('EQUIPMENT_CONFIG_CHANGED'));
    this.wizardConditionRows.set(this.parseConditionExpressionToRows((row.customCondition ?? '').trim()));
    if (kind === 'ADMIN') {
      this.subscriptionWizardOpen.set(true);
      this.subscriptionWizardStep.set(1);
      return;
    }

    this.wizardAvailabilityDraft.set('ALL');
    this.wizardSearchDraft.set('');
    this.wizardIpDraft.set('');
    this.wizardMacDraft.set('');
    this.wizardStatusDraft.set('');
    this.wizardTagDraft.set([]);
    this.wizardTagInputDraft.set('');
    this.wizardHealthDraft.set('ALL');
    this.wizardSelectedDeviceIds.set([]);
    this.wizardSelectedMetricIds.set([]);
    this.wizardMetricOptions.set([]);
    const requestedIps = new Set(
      (row.deviceIpFilter ?? '')
        .split(',')
        .map((v) => v.trim())
        .filter((v) => !!v)
    );
    const requestedMetricNames = new Set(
      (row.metricFilter ?? '')
        .split(',')
        .map((v) => v.trim().toLowerCase())
        .filter((v) => !!v)
    );
    this.loadWizardDevices(() => {
      if (requestedIps.size > 0) {
        const matchedDeviceIds = this.wizardDevices()
          .filter((d) => requestedIps.has(d.ip))
          .map((d) => String(d.id));
        this.wizardSelectedDeviceIds.set(matchedDeviceIds);
      }
      this.loadWizardMetrics(() => {
        if (requestedMetricNames.size > 0) {
          const matchedMetricIds = this.wizardMetricOptions()
            .filter((m) => requestedMetricNames.has((m.metricName ?? '').trim().toLowerCase()))
            .map((m) => m.id);
          this.wizardSelectedMetricIds.set(matchedMetricIds);
        }
      });
    });
    this.subscriptionWizardOpen.set(true);
    this.subscriptionWizardStep.set(3);
  }

  protected removeSubscription(id: number | null): void {
    if (!id) {
      return;
    }
    this.subscriptionsSaving.set(true);
    this.settingsService.deleteNotificationSubscription(id).subscribe({
      next: () => {
        this.notify.success('Подписка удалена.', 'Подписки уведомлений');
        this.subscriptionsSaving.set(false);
        this.loadSubscriptions();
      },
      error: () => {
        this.notify.error('Не удалось удалить подписку.', 'Подписки уведомлений');
        this.subscriptionsSaving.set(false);
      },
    });
  }

  protected openSubscriptionWizard(): void {
    const kind = this.preferredNotificationKind();
    this.wizardNotificationKind.set(kind);
    this.subscriptionWizardOpen.set(true);
    this.subscriptionWizardStep.set(1);
    this.wizardEditingSubscriptionId.set(null);
    this.wizardBaseSubscription.set(null);
    this.wizardBaseCustomCondition.set('');
    this.wizardSelectedDeviceIds.set([]);
    this.wizardSelectedMetricIds.set([]);
    this.wizardRecipientEmail.set(this.auth.authSession()?.email ?? '');
    this.wizardAdminEventCodes.set([
      'USER_CREATED',
      'USER_LOGIN',
      'USER_BLOCKED',
      'USER_LOGIN_FAILED',
      'MONITORING_SETTINGS_CHANGED',
      'EQUIPMENT_CONFIG_CHANGED',
      'TEMPLATE_CHANGED',
      'MONITORING_TEMPLATE_APPLIED_TO_DEVICE',
      'DEVICE_UNMONITORED',
    ]);
    this.wizardSeveritySelection.set([]);
    this.wizardNotifyDeviceUnmonitored.set(true);
    this.wizardNotifyConfigChanged.set(true);
    this.wizardConditionRows.set([]);
    if (kind === 'OPERATOR') {
      this.loadWizardDevices();
    }
  }

  protected openTestEventDialogForSubscription(row: NotificationSubscription): void {
    const kind: 'ADMIN' | 'OPERATOR' = row.notificationKind === 'ADMIN' ? 'ADMIN' : 'OPERATOR';
    const fallbackCode = kind === 'ADMIN' ? 'ADMIN_ANY' : 'MONITORING_EVENT_OPEN';
    const eventCode = (row.eventCodes ?? []).find((code) => !!code?.trim()) ?? fallbackCode;
    this.testEventKind.set(kind);
    this.testEventCode.set(eventCode);

    const firstIp = (row.deviceIpFilter ?? '')
      .split(',')
      .map((v) => v.trim())
      .find((v) => !!v);
    if (firstIp) {
      this.testEventDeviceIp.set(firstIp);
    }

    const firstMetric = (row.metricFilter ?? '')
      .split(',')
      .map((v) => v.trim())
      .find((v) => !!v);
    if (firstMetric) {
      this.testEventMetricName.set(firstMetric);
    }

    const firstSeverity = this.parseSeverityFilter(row.severityFilter)[0];
    if (firstSeverity) {
      this.testEventSeverity.set(firstSeverity);
    }

    const firstTag = (row.deviceTagFilter ?? '')
      .split(',')
      .map((v) => v.trim())
      .filter((v) => !!v)
      .join(',');
    if (firstTag) {
      this.testEventDeviceTags.set(firstTag);
    }

    this.testEventDetails.set(`Тест по подписке #${row.id ?? 'new'}`);
    this.testEventDialogOpen.set(true);
  }

  protected availableTestEventOptions(): Array<{ label: string; value: string }> {
    return this.testEventKind() === 'ADMIN' ? this.testAdminEventOptions : this.testOperatorEventOptions;
  }

  protected eventLabels(codes: string[] | null | undefined): string {
    const items = (codes ?? [])
      .map((code) => this.eventLabelByCode[code] ?? code)
      .filter((v) => !!v);
    return items.length > 0 ? items.join(', ') : '—';
  }

  protected sendTestEvent(): void {
    const payload = {
      notificationKind: this.testEventKind(),
      eventCode: this.testEventCode(),
      deviceIp: this.testEventDeviceIp(),
      deviceName: this.testEventDeviceName(),
      severity: this.testEventSeverity(),
      metricName: this.testEventMetricName(),
      deviceTags: this.testEventDeviceTags(),
      details: this.testEventDetails(),
    };
    this.testEventDialogOpen.set(false);
    this.testEventSending.set(true);
    this.settingsService.sendTestNotificationEvent(payload).subscribe({
      next: () => {
        this.testEventSending.set(false);
        this.notify.success('Тестовое событие отправлено.', 'Подписки уведомлений');
      },
      error: () => {
        this.testEventSending.set(false);
        this.notify.error('Не удалось отправить тестовое событие.', 'Подписки уведомлений');
      },
    });
  }

  protected closeSubscriptionWizard(): void {
    this.subscriptionWizardOpen.set(false);
    this.subscriptionWizardStep.set(1);
    this.wizardNotificationKind.set(this.preferredNotificationKind());
    this.wizardEditingSubscriptionId.set(null);
    this.wizardBaseSubscription.set(null);
    this.wizardBaseCustomCondition.set('');
  }

  protected applyWizardFilters(): void {
    this.commitWizardTagInput();
    this.loadWizardDevices();
  }

  protected resetWizardFilters(): void {
    this.wizardAvailabilityDraft.set('ALL');
    this.wizardSearchDraft.set('');
    this.wizardIpDraft.set('');
    this.wizardMacDraft.set('');
    this.wizardStatusDraft.set('');
    this.wizardTagDraft.set([]);
    this.wizardTagInputDraft.set('');
    this.wizardHealthDraft.set('ALL');
    this.loadWizardDevices();
  }

  protected addWizardTagFromInput(event?: Event): void {
    event?.preventDefault();
    this.commitWizardTagInput();
  }

  protected removeWizardTag(tag: string): void {
    this.wizardTagDraft.update((tags) => tags.filter((t) => t !== tag));
  }

  protected isWizardDeviceSelected(id: string): boolean {
    return this.wizardSelectedDeviceIds().includes(String(id));
  }

  protected areAllVisibleWizardDevicesSelected(): boolean {
    const visibleIds = this.wizardDevices().map((d) => String(d.id));
    return visibleIds.length > 0 && visibleIds.every((id) => this.wizardSelectedDeviceIds().includes(id));
  }

  protected setAllVisibleWizardDevicesSelected(selected: boolean): void {
    const visibleIds = this.wizardDevices().map((d) => String(d.id));
    this.wizardSelectedDeviceIds.update((ids) => {
      const set = new Set(ids);
      if (selected) {
        visibleIds.forEach((id) => set.add(id));
      } else {
        visibleIds.forEach((id) => set.delete(id));
      }
      return [...set];
    });
  }

  protected setWizardDeviceSelected(id: string, selected: boolean): void {
    const normalizedId = String(id);
    this.wizardSelectedDeviceIds.update((ids) => {
      const set = new Set(ids);
      if (selected) {
        set.add(normalizedId);
      } else {
        set.delete(normalizedId);
      }
      return [...set];
    });
  }

  protected goToWizardStep2(): void {
    if (this.wizardSelectedDeviceIds().length === 0) {
      this.notify.warn('Выберите хотя бы одно устройство.', 'Подписки уведомлений');
      return;
    }
    this.subscriptionWizardStep.set(2);
    this.wizardMetricSearch.set('');
    this.loadWizardMetrics();
  }

  protected backToWizardStep1(): void {
    this.subscriptionWizardStep.set(1);
  }

  protected goToWizardStep3(): void {
    if (!this.wizardRecipientEmail().trim()) {
      this.wizardRecipientEmail.set(this.auth.authSession()?.email ?? '');
    }
    this.subscriptionWizardStep.set(3);
  }

  protected backToWizardStep2(): void {
    if (this.wizardSelectedDeviceIds().length > 0 && this.wizardMetricOptions().length === 0 && !this.wizardMetricsLoading()) {
      this.loadWizardMetrics();
    }
    this.subscriptionWizardStep.set(2);
  }

  protected toggleWizardMetric(id: string, selected: boolean): void {
    this.wizardSelectedMetricIds.update((ids) => {
      const set = new Set(ids);
      if (selected) {
        set.add(id);
      } else {
        set.delete(id);
      }
      return [...set];
    });
  }

  protected isWizardMetricSelected(id: string): boolean {
    return this.wizardSelectedMetricIds().includes(id);
  }

  protected areAllFilteredWizardMetricsSelected(): boolean {
    const filteredIds = this.wizardFilteredMetricOptions().map((m) => m.id);
    return filteredIds.length > 0 && filteredIds.every((id) => this.wizardSelectedMetricIds().includes(id));
  }

  protected setAllFilteredWizardMetricsSelected(selected: boolean): void {
    const filteredIds = this.wizardFilteredMetricOptions().map((m) => m.id);
    this.wizardSelectedMetricIds.update((ids) => {
      const set = new Set(ids);
      if (selected) {
        filteredIds.forEach((id) => set.add(id));
      } else {
        filteredIds.forEach((id) => set.delete(id));
      }
      return [...set];
    });
  }

  protected addConditionRow(): void {
    this.wizardConditionRows.update((rows) => [
      ...rows,
      { field: 'event', value: '', joinWithNext: '&&', negated: false },
    ]);
  }

  protected removeConditionRow(index: number): void {
    this.wizardConditionRows.update((rows) => rows.filter((_, i) => i !== index));
  }

  protected updateConditionRowField(index: number, field: 'event' | 'severity' | 'metric' | 'ip' | 'tag'): void {
    this.wizardConditionRows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, field } : r))
    );
  }

  protected updateConditionRowValue(index: number, value: string): void {
    this.wizardConditionRows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, value } : r))
    );
  }

  protected conditionValueOptions(index: number): Array<{ label: string; value: string }> {
    const row = this.wizardConditionRows()[index];
    if (!row) {
      return [];
    }
    const values = this.conditionValuesByField(row.field);
    return values.map((v) => ({ label: this.conditionValueLabel(row.field, v), value: v }));
  }

  private conditionValuesByField(field: 'event' | 'severity' | 'metric' | 'ip' | 'tag'): string[] {
    if (field === 'event') {
      const events: string[] = ['MONITORING_EVENT_OPEN'];
      if (this.wizardNotifyDeviceUnmonitored()) {
        events.push('DEVICE_UNMONITORED');
      }
      if (this.wizardNotifyConfigChanged()) {
        events.push('EQUIPMENT_CONFIG_CHANGED');
      }
      return [...new Set(events)];
    }
    if (field === 'severity') {
      const selected = this.wizardSeveritySelection();
      if (selected.length > 0) {
        return [...new Set(selected)];
      }
      return this.severityFilterOptions.map((o) => o.value);
    }
    if (field === 'metric') {
      const metricMap = new Map(this.wizardMetricOptions().map((m) => [m.id, m.metricName]));
      const selected = this.wizardSelectedMetricIds()
        .map((id) => metricMap.get(id))
        .filter((v): v is string => !!v && v.trim().length > 0);
      return [...new Set(selected)];
    }
    if (field === 'ip') {
      const deviceMap = new Map(this.wizardDevices().map((d) => [String(d.id), d]));
      const selected = this.wizardSelectedDeviceIds()
        .map((id) => deviceMap.get(id)?.ip)
        .filter((v): v is string => !!v && v.trim().length > 0);
      return [...new Set(selected)];
    }
    const deviceMap = new Map(this.wizardDevices().map((d) => [String(d.id), d]));
    const tags: string[] = [];
    this.wizardSelectedDeviceIds().forEach((id) => {
      const device = deviceMap.get(id);
      (device?.tags ?? []).forEach((t) => {
        if (t && t.trim()) {
          tags.push(t.trim());
        }
      });
    });
    return [...new Set(tags)];
  }

  private conditionValueLabel(field: 'event' | 'severity' | 'metric' | 'ip' | 'tag', value: string): string {
    if (field === 'severity') {
      return this.severityLabelByCode[value] ?? value;
    }
    if (field === 'event') {
      return this.eventLabelByCode[value] ?? value;
    }
    return value;
  }

  protected updateConditionRowJoin(index: number, joinWithNext: '&&' | '||'): void {
    this.wizardConditionRows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, joinWithNext } : r))
    );
  }

  protected updateConditionRowNegated(index: number, negated: boolean): void {
    this.wizardConditionRows.update((rows) =>
      rows.map((r, i) => (i === index ? { ...r, negated } : r))
    );
  }

  protected wizardConditionExpression(): string {
    const rows = this.wizardConditionRows()
      .map((r) => ({
        ...r,
        value: r.value.trim(),
      }))
      .filter((r) => r.value.length > 0);
    if (rows.length === 0) {
      return '';
    }
    return rows
      .map((r, index) => {
        const prefix = r.negated ? '!' : '';
        const expr = `${prefix}${r.field}=${r.value}`;
        const hasNext = index < rows.length - 1;
        return hasNext ? `${expr} ${r.joinWithNext}` : expr;
      })
      .join(' ');
  }

  private parseConditionExpressionToRows(expression: string): Array<{
    field: 'event' | 'severity' | 'metric' | 'ip' | 'tag';
    value: string;
    joinWithNext: '&&' | '||';
    negated: boolean;
  }> {
    const src = expression.trim();
    if (!src) {
      return [];
    }
    const rows: Array<{
      field: 'event' | 'severity' | 'metric' | 'ip' | 'tag';
      value: string;
      joinWithNext: '&&' | '||';
      negated: boolean;
    }> = [];

    const parts = src.split(/(\&\&|\|\|)/).map((p) => p.trim()).filter((p) => p.length > 0);
    let pendingJoin: '&&' | '||' = '&&';
    for (const part of parts) {
      if (part === '&&' || part === '||') {
        pendingJoin = part;
        continue;
      }
      const negated = part.startsWith('!');
      const token = negated ? part.slice(1).trim() : part;
      const eqIdx = token.indexOf('=');
      if (eqIdx <= 0 || eqIdx >= token.length - 1) {
        continue;
      }
      const keyRaw = token.slice(0, eqIdx).trim().toLowerCase();
      const value = token.slice(eqIdx + 1).trim();
      const field = this.normalizeConditionField(keyRaw);
      if (!field || !value) {
        continue;
      }
      rows.push({
        field,
        value,
        joinWithNext: pendingJoin,
        negated,
      });
      pendingJoin = '&&';
    }
    return rows;
  }

  private normalizeConditionField(raw: string): 'event' | 'severity' | 'metric' | 'ip' | 'tag' | null {
    if (raw === 'event' || raw === 'eventcode') return 'event';
    if (raw === 'severity') return 'severity';
    if (raw === 'metric' || raw === 'metricname') return 'metric';
    if (raw === 'ip' || raw === 'deviceip') return 'ip';
    if (raw === 'tag' || raw === 'devicetag') return 'tag';
    return null;
  }

  protected saveSubscriptionFromWizard(): void {
    if (this.wizardNotificationKind() === 'ADMIN') {
      const recipientEmail = this.wizardRecipientEmail().trim();
      if (!recipientEmail) {
        this.notify.warn('Укажите email получателя.', 'Подписки уведомлений');
        return;
      }
      const eventCodes = this.wizardAdminEventCodes();
      if (eventCodes.length === 0) {
        this.notify.warn('Выберите хотя бы одно административное событие.', 'Подписки уведомлений');
        return;
      }
      const payload: NotificationSubscription = {
        id: this.wizardEditingSubscriptionId(),
        enabled: true,
        notificationKind: 'ADMIN',
        subscriptionType: 'SYSTEM',
        channel: 'SMTP',
        eventCodes,
        recipientEmail,
        deviceIpFilter: null,
        deviceTagFilter: null,
        severityFilter: null,
        metricFilter: null,
        customCondition: null,
      };
      this.subscriptionsSaving.set(true);
      this.settingsService.upsertNotificationSubscription(payload).subscribe({
        next: () => {
          this.subscriptionsSaving.set(false);
          this.notify.success(
            this.wizardEditingSubscriptionId() == null ? 'Подписка создана.' : 'Подписка обновлена.',
            'Подписки уведомлений'
          );
          this.closeSubscriptionWizard();
          this.loadSubscriptions();
        },
        error: () => {
          this.subscriptionsSaving.set(false);
          this.notify.error('Не удалось создать подписку.', 'Подписки уведомлений');
        },
      });
      return;
    }

    const recipientEmail = this.wizardRecipientEmail().trim();
    if (!recipientEmail) {
      this.notify.warn('Укажите email получателя.', 'Подписки уведомлений');
      return;
    }
    const selectedDeviceMap = new Map(this.wizardDevices().map((d) => [String(d.id), d]));
    const selectedDevices = this.wizardSelectedDeviceIds()
      .map((id) => selectedDeviceMap.get(id))
      .filter((d): d is MonitoringDeviceListItem => !!d);
    const editingBase = this.wizardBaseSubscription();
    const isEditMode = this.wizardEditingSubscriptionId() != null;
    if (!isEditMode && selectedDevices.length === 0) {
      this.notify.warn('Выберите хотя бы одно устройство.', 'Подписки уведомлений');
      return;
    }

    const metricById = new Map(this.wizardMetricOptions().map((m) => [m.id, m]));
    const selectedMetricIds = this.wizardSelectedMetricIds().length > 0
      ? this.wizardSelectedMetricIds()
      : this.wizardMetricOptions().map((m) => m.id);
    const selectedMetrics = selectedMetricIds
      .map((id) => metricById.get(id))
      .filter((m): m is NonNullable<typeof m> => !!m);
    if (!isEditMode && selectedMetrics.length === 0 && this.wizardMetricOptions().length > 0) {
      this.notify.warn('Не удалось определить метрики для подписки.', 'Подписки уведомлений');
      return;
    }

    const eventCodes: NotificationEventCode[] = ['MONITORING_EVENT_OPEN'];
    if (this.wizardNotifyDeviceUnmonitored()) {
      eventCodes.push('DEVICE_UNMONITORED');
    }
    if (this.wizardNotifyConfigChanged()) {
      eventCodes.push('EQUIPMENT_CONFIG_CHANGED');
    }

    const uniqueIps = [...new Set(selectedDevices.map((d) => d.ip).filter((v) => !!v))];
    const uniqueMetrics = [...new Set(selectedMetrics.map((m) => m.metricName).filter((v) => !!v))];
    const customConditionExpr = this.wizardConditionExpression();
    const customCondition = customConditionExpr || this.wizardBaseCustomCondition();

    const payload: NotificationSubscription = {
      id: this.wizardEditingSubscriptionId(),
      enabled: true,
      notificationKind: 'OPERATOR',
      subscriptionType: 'DEVICE',
      channel: 'SMTP',
      eventCodes,
      recipientEmail,
      deviceIpFilter: uniqueIps.length > 0
        ? uniqueIps.join(',')
        : (editingBase?.deviceIpFilter ?? null),
      deviceTagFilter: null,
      severityFilter: this.wizardSeveritySelection().length > 0 ? this.wizardSeveritySelection().join(',') : null,
      metricFilter: uniqueMetrics.length > 0
        ? uniqueMetrics.join(',')
        : (editingBase?.metricFilter ?? null),
      customCondition: customCondition || null,
    };

    this.subscriptionsSaving.set(true);
    this.settingsService.upsertNotificationSubscription(payload).subscribe({
      next: () => {
        this.subscriptionsSaving.set(false);
        this.notify.success(
          this.wizardEditingSubscriptionId() == null ? 'Подписка создана.' : 'Подписка обновлена.',
          'Подписки уведомлений'
        );
        this.closeSubscriptionWizard();
        this.loadSubscriptions();
      },
      error: () => {
        this.subscriptionsSaving.set(false);
        this.notify.error('Не удалось создать подписку.', 'Подписки уведомлений');
      },
    });
  }

  protected setAdminEventSelected(code: NotificationEventCode, selected: boolean): void {
    this.wizardAdminEventCodes.update((codes) => {
      if (selected) {
        return [...new Set([...codes, code])];
      }
      return codes.filter((v) => v !== code);
    });
  }

  private loadWizardDevices(onLoaded?: () => void): void {
    this.wizardDevicesLoading.set(true);
    const params: Record<string, string> = {
      page: '0',
      size: '100',
      sortField: 'ip',
      sortOrder: 'asc',
    };
    if (this.wizardSearchDraft().trim()) params['q'] = this.wizardSearchDraft().trim();
    if (this.wizardIpDraft().trim()) params['ip'] = this.wizardIpDraft().trim();
    if (this.wizardMacDraft().trim()) params['macAddress'] = this.wizardMacDraft().trim();
    if (this.wizardStatusDraft().trim()) params['status'] = this.wizardStatusDraft().trim();
    if (this.wizardTagDraft().length > 0) params['tag'] = this.wizardTagDraft().join(',');
    if (this.wizardHealthDraft() !== 'ALL') params['healthStatus'] = this.wizardHealthDraft();
    if (this.wizardAvailabilityDraft() !== 'ALL') params['availability'] = this.wizardAvailabilityDraft();

    this.http.get<MonitoringDevicePage>(`${this.apiBaseUrl}/api/monitoring`, { params }).subscribe({
      next: (page) => {
        this.wizardDevices.set(page?.content ?? []);
        const availableIds = new Set((page?.content ?? []).map((d) => String(d.id)));
        this.wizardSelectedDeviceIds.update((ids) => ids.filter((id) => availableIds.has(id)));
        this.wizardDevicesLoading.set(false);
        onLoaded?.();
      },
      error: () => {
        this.wizardDevices.set([]);
        this.wizardSelectedDeviceIds.set([]);
        this.wizardDevicesLoading.set(false);
        this.notify.error('Не удалось загрузить список устройств для шага 1.', 'Подписки уведомлений');
      },
    });
  }

  private loadWizardMetrics(onLoaded?: () => void): void {
    const selectedIds = this.wizardSelectedDeviceIds();
    if (selectedIds.length === 0) {
      this.wizardMetricOptions.set([]);
      this.wizardSelectedMetricIds.set([]);
      return;
    }
    this.wizardMetricsLoading.set(true);
    const requests = selectedIds.map((deviceId) =>
      this.http.get<MonitoringDeviceItem[]>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/items`)
    );
    forkJoin(requests).subscribe({
      next: (itemsByDevice) => {
        const deviceMap = new Map(this.wizardDevices().map((d) => [String(d.id), d]));
        const options: Array<{
          id: string;
          label: string;
          deviceId: string;
          deviceName: string;
          metricName: string;
          itemUuid: string;
          instanceKey: string | null;
        }> = [];
        itemsByDevice.forEach((items, index) => {
          const deviceId = String(selectedIds[index]);
          const device = deviceMap.get(deviceId);
          const deviceName = device?.name || device?.ip || `Устройство ${deviceId}`;
          (items ?? []).forEach((item) => {
            const optionId = `${deviceId}|${item.itemUuid}|${item.instanceKey ?? ''}`;
            options.push({
              id: optionId,
              label: `${deviceName}: ${item.name} (${item.itemKey})`,
              deviceId,
              deviceName,
              metricName: item.name,
              itemUuid: item.itemUuid,
              instanceKey: item.instanceKey ?? null,
            });
          });
        });
        this.wizardMetricOptions.set(options);
        const allowedIds = new Set(options.map((o) => o.id));
        this.wizardSelectedMetricIds.update((ids) => ids.filter((id) => allowedIds.has(id)));
        this.wizardMetricsLoading.set(false);
        onLoaded?.();
      },
      error: () => {
        this.wizardMetricOptions.set([]);
        this.wizardSelectedMetricIds.set([]);
        this.wizardMetricsLoading.set(false);
        this.notify.error('Не удалось загрузить метрики для выбранных устройств.', 'Подписки уведомлений');
      },
    });
  }

  private commitWizardTagInput(): void {
    const raw = this.wizardTagInputDraft().trim();
    if (!raw) {
      return;
    }
    this.wizardTagDraft.update((tags) => {
      const set = new Set(tags);
      raw
        .split(',')
        .map((v) => v.trim())
        .filter((v) => !!v)
        .forEach((v) => set.add(v));
      return [...set];
    });
    this.wizardTagInputDraft.set('');
  }

  protected updateSubscriptionDraft<K extends keyof NotificationSubscription>(
    key: K,
    value: NotificationSubscription[K]
  ): void {
    this.subscriptionDraft.update((v) => {
      const next = { ...v, [key]: value };
      if (key === 'notificationKind') {
        next.eventCodes = [(value as unknown as string) === 'ADMIN' ? 'ADMIN_ANY' : 'NEW_DEVICE_DISCOVERED'];
        next.subscriptionType = (value as unknown as string) === 'ADMIN' ? 'SYSTEM' : 'DEVICE';
      }
      if (key === 'subscriptionType') {
        const subType = String(value ?? '');
        if (subType === 'SCAN_JOB') {
          next.eventCodes = ['SCAN_JOB_SCHEDULED'];
          next.deviceIpFilter = null;
          next.deviceTagFilter = null;
          next.severityFilter = null;
          next.metricFilter = null;
          next.customCondition = null;
          this.severityFilterSelection.set([]);
        } else if (subType === 'TAG_GROUP') {
          next.eventCodes = ['MONITORING_EVENT_OPEN'];
          next.deviceIpFilter = null;
        } else if (subType === 'DEVICE') {
          next.eventCodes = ['MONITORING_EVENT_OPEN'];
          next.deviceTagFilter = null;
        }
      }
      return next;
    });
  }

  protected subscriptionTypeOptions(): Array<{ label: string; value: NotificationSubscriptionType }> {
    return this.subscriptionDraft().notificationKind === 'ADMIN'
      ? this.adminSubscriptionTypeOptions
      : this.operatorSubscriptionTypeOptions;
  }

  protected eventOptionsForDraft(): Array<{ label: string; value: NotificationEventCode }> {
    if (this.subscriptionDraft().notificationKind === 'ADMIN') {
      return this.adminEventOptions;
    }
    return this.subscriptionDraft().subscriptionType === 'SCAN_JOB'
      ? this.scanJobEventOptions
      : this.operatorEventOptions;
  }

  protected updateSeverityFilterValues(values: string[] | null | undefined): void {
    const normalizedValues = (values ?? [])
      .map((v) => (v ?? '').trim().toUpperCase())
      .filter((v) => !!v);
    this.severityFilterSelection.set(normalizedValues);
    this.updateSubscriptionDraft('severityFilter', normalizedValues.join(',') || null);
  }

  private parseSeverityFilter(raw: string | null | undefined): string[] {
    if (!raw || !raw.trim()) {
      return [];
    }
    return raw
      .split(',')
      .map((v) => v.trim().toUpperCase())
      .filter((v) => !!v);
  }

  protected preferredNotificationKind(): 'ADMIN' | 'OPERATOR' {
    return this.isAdmin() ? 'ADMIN' : 'OPERATOR';
  }

  protected loadRoleMappings(): void {
    this.loadingGroups.set(true);
    this.settingsService.listDirectoryRoleMappings().subscribe({
      next: (rows) => {
        this.mappingRows.set(rows);
        this.loadingGroups.set(false);
      },
      error: () => {
        this.mappingRows.set([]);
        this.loadingGroups.set(false);
      },
    });
  }

  protected discoverGroups(): void {
    this.loadingGroups.set(true);
    this.settingsService.discoverDirectoryGroups().subscribe({
      next: (groups) => {
        this.discoveredGroups.set(groups);
        const byDn = new Map(this.mappingRows().map((r) => [r.groupDn.toLowerCase(), r]));
        const merged = groups.map((g) => {
          const existing = byDn.get(g.groupDn.toLowerCase());
          return {
            groupDn: g.groupDn,
            groupName: g.groupName,
            role: existing?.role ?? '',
          } satisfies DirectoryRoleMapping;
        });
        this.mappingRows.set(merged);
        this.loadingGroups.set(false);
      },
      error: () => {
        this.notify.error('Не удалось получить группы из каталога.', 'Соответствие ролей');
        this.loadingGroups.set(false);
      },
    });
  }

  protected updateMappingRole(groupDn: string, role: AppRole | ''): void {
    this.mappingRows.update((rows) =>
      rows.map((row) => (row.groupDn === groupDn ? { ...row, role } : row))
    );
  }

  protected saveRoleMappings(): void {
    this.savingMappings.set(true);
    this.settingsService.updateDirectoryRoleMappings({ items: this.mappingRows() }).subscribe({
      next: (saved) => {
        this.mappingRows.set(saved);
        this.notify.success('Соответствие групп и ролей сохранено.', 'Соответствие ролей');
        this.savingMappings.set(false);
      },
      error: () => {
        this.notify.error('Не удалось сохранить соответствие ролей.', 'Соответствие ролей');
        this.savingMappings.set(false);
      },
    });
  }

  protected searchDirectoryUsersForCreation(): void {
    if (!this.directoryUserFilter().trim()) {
      this.notify.warn('Заполните LDAP-фильтр пользователей.', 'Создание пользователя');
      return;
    }
    this.loadingGroups.set(true);
    this.settingsService.searchDirectoryUsers({
      ldapFilter: this.directoryUserFilter().trim(),
      emailAttribute: this.createEmailAttribute().trim(),
      displayNameAttribute: this.createDisplayNameAttribute().trim(),
    }).subscribe({
      next: (users) => {
        this.foundDirectoryUsers.set(users);
        this.selectedDirectoryDn.set(users[0]?.directoryDn ?? null);
        if (users[0]) {
          this.applySuggestedRole(users[0]);
        }
        this.loadingGroups.set(false);
      },
      error: (error) => {
        const message =
          error?.error?.message && typeof error.error.message === 'string'
            ? error.error.message
            : 'Не удалось получить пользователей из LDAP/AD.';
        this.notify.error(message, 'Создание пользователя');
        this.loadingGroups.set(false);
      },
    });
  }

  protected createSelectedDirectoryUser(): void {
    const selectedDn = this.selectedDirectoryDn();
    const candidate = this.foundDirectoryUsers().find((u) => u.directoryDn === selectedDn);
    if (!candidate) {
      this.notify.warn('Выберите пользователя из списка.', 'Создание пользователя');
      return;
    }
    const payload: CreateUserFromDirectoryRequest = {
      directoryDn: candidate.directoryDn,
      login: candidate.login,
      email: candidate.email,
      displayName: candidate.displayName,
      role: this.createDirectoryRole(),
      enabled: this.createDirectoryUserEnabled(),
    };
    this.savingMappings.set(true);
    this.settingsService.createUserFromDirectory(payload).subscribe({
      next: (created) => {
        this.notify.success(`Пользователь ${created.email} создан.`, 'Создание пользователя');
        this.savingMappings.set(false);
      },
      error: (error) => {
        const message =
          error?.error?.message && typeof error.error.message === 'string'
            ? error.error.message
            : 'Не удалось создать пользователя из каталога.';
        this.notify.error(message, 'Создание пользователя');
        this.savingMappings.set(false);
      },
    });
  }

  protected onSelectDirectoryUser(dn: string): void {
    this.selectedDirectoryDn.set(dn);
    const candidate = this.foundDirectoryUsers().find((u) => u.directoryDn === dn);
    if (candidate) {
      this.applySuggestedRole(candidate);
    }
  }

  private applySuggestedRole(candidate: DirectoryUserCandidate): void {
    const groupRoleMap = new Map(
      this.mappingRows()
        .filter((m) => m.role && m.groupDn)
        .map((m) => [m.groupDn.toLowerCase(), m.role as AppRole])
    );
    const matched = (candidate.groupDns ?? [])
      .map((g) => groupRoleMap.get(g.toLowerCase()))
      .filter((v): v is AppRole => !!v);
    const ordered: AppRole[] = ['ADMIN', 'OPERATOR', 'VIEWER'];
    const best = ordered.find((r) => matched.includes(r));
    if (best) {
      this.createDirectoryRole.set(best);
    }
  }

  private extractHttpErrorMessage(error: unknown, fallback: string): string {
    const e = error as {
      status?: number;
      error?: unknown;
      message?: string;
    } | null;
    const payload = e?.error;
    if (payload && typeof payload === 'object') {
      const maybeMessage = (payload as { message?: unknown }).message;
      if (typeof maybeMessage === 'string' && maybeMessage.trim()) {
        return maybeMessage;
      }
    }
    if (typeof payload === 'string' && payload.trim()) {
      return payload;
    }
    if (typeof e?.message === 'string' && e.message.trim()) {
      return e.message;
    }
    if (typeof e?.status === 'number' && e.status > 0) {
      if (e.status === 403) {
        return 'Недостаточно прав для операции. Требуется роль ADMIN.';
      }
      if (e.status === 401) {
        return 'Сессия истекла или отсутствует авторизация. Войдите в систему снова.';
      }
      return `${fallback} (HTTP ${e.status})`;
    }
    return fallback;
  }
}
