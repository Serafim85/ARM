import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { Popover, PopoverModule } from 'primeng/popover';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../../auth.service';
import { API_BASE_URL } from '../../api-config';
import { DEMO_SEED_STORAGE_KEY } from '../../demo-seed.constants';
import { NotifierService } from '../../notifier.service';
import { AppVersionInfoComponent } from '../../components/app-version-info/app-version-info.component';
import { SystemSettingsService } from '../../services/system-settings.service';

type DemoSeedResponse = {
  alreadySeeded: boolean;
  devicesCreated: number;
  message: string;
};

type TestAccount = {
  login: string;
  password: string;
};

const LOCAL_TEST_ACCOUNTS: TestAccount[] = [
  { login: 'admin@example.com', password: 'password' },
  { login: 'operator@example.com', password: 'operator123' },
  { login: 'viewer@example.com', password: 'viewer123' },
];

const LDAP_TEST_ACCOUNTS: TestAccount[] = [
  { login: 'admin', password: 'password' },
  { login: 'operator', password: 'operator123' },
  { login: 'viewer', password: 'viewer123' },
];

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    PopoverModule,
    TooltipModule,
    AppVersionInfoComponent,
  ],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.css',
})
export class LoginPageComponent {
  protected readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly notify = inject(NotifierService);
  private readonly settingsService = inject(SystemSettingsService);

  protected readonly demoSeedLoading = signal(false);
  protected readonly debugMode = signal(false);
  protected readonly demoSeedUsed = signal(
    typeof localStorage !== 'undefined' && localStorage.getItem(DEMO_SEED_STORAGE_KEY) === '1'
  );

  private readonly testAccountsPopover = viewChild<Popover>('testAccountsPopover');

  protected readonly testAccounts = computed(() =>
    this.auth.loginMode() === 'LDAP' ? LDAP_TEST_ACCOUNTS : LOCAL_TEST_ACCOUNTS
  );

  protected readonly testAccountsLabel = computed(() =>
    this.auth.loginMode() === 'LDAP'
      ? 'Тестовые LDAP-учётные записи'
      : 'Тестовые локальные учётные записи'
  );

  constructor() {
    this.settingsService.getAppConfig().subscribe({
      next: (config) => {
        this.debugMode.set(!!config?.debugMode);
      },
      error: () => {
        this.debugMode.set(false);
      },
    });
  }

  protected applyTestAccount(account: TestAccount): void {
    this.auth.email.set(account.login);
    this.auth.password.set(account.password);
    this.testAccountsPopover()?.hide();
  }

  protected seedDemoMonitoring(): void {
    if (this.demoSeedUsed() || this.demoSeedLoading()) {
      return;
    }
    this.demoSeedLoading.set(true);
    this.http
      .post<DemoSeedResponse>(`${this.apiBaseUrl}/api/debug/demo-monitoring-seed`, {})
      .subscribe({
        next: (response) => {
          localStorage.setItem(DEMO_SEED_STORAGE_KEY, '1');
          this.demoSeedUsed.set(true);
          this.demoSeedLoading.set(false);
          this.notify.success(response.message, 'Демо-данные');
        },
        error: () => {
          this.demoSeedLoading.set(false);
          this.notify.error('Не удалось загрузить демо-данные. Проверьте, что бэкенд запущен.', 'Демо-данные');
        },
      });
  }
}
