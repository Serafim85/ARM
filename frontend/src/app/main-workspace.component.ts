import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  inject,
  PLATFORM_ID,
  signal,
  ViewEncapsulation
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IsActiveMatchOptions,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from '@angular/router';
import { debounceTime, filter, fromEvent } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { AppVersionInfoComponent } from './components/app-version-info/app-version-info.component';
import { AppRole, AuthService } from './auth.service';
import { MonitoringService } from './services/monitoring.service';
import { environment } from '../environments/environment';

type NavigationItem = {
  navLabel: string;
  topbarTitle: string;
  icon:
    | 'scan'
    | 'scanJobs'
    | 'monitoring'
    | 'topology'
    | 'templates'
    | 'events'
    | 'dashboards'
    | 'audit'
    | 'users'
    | 'systemSettings';
  /** PrimeIcons class, например pi pi-search */
  primeIcon: string;
  adminOnly?: boolean;
};

@Component({
  selector: 'app-main-workspace',
  imports: [
    FormsModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    ButtonModule,
    TooltipModule,
    AppVersionInfoComponent,
  ],
  templateUrl: './main-workspace.component.html',
  styleUrl: './main-workspace.component.css',
  encapsulation: ViewEncapsulation.None
})
export class MainWorkspaceComponent {
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);
  private readonly mon = inject(MonitoringService);

  protected readonly navigationItems: NavigationItem[] = (
    [
    {
      navLabel: 'Дашборды',
      topbarTitle: 'Дашборды',
      icon: 'dashboards',
      primeIcon: 'pi pi-chart-bar'
    },
    {
      navLabel: 'Сканирование',
      topbarTitle: 'Сканирование сетевого оборудования',
      icon: 'scan',
      primeIcon: 'pi pi-search'
    },
    {
      navLabel: 'Автосканирование',
      topbarTitle: 'Задачи автосканирования',
      icon: 'scanJobs',
      primeIcon: 'pi pi-clock'
    },
    {
      navLabel: environment.hideNetworkScannerFeatures ? 'АРМ' : 'Устройства',
      topbarTitle: environment.hideNetworkScannerFeatures ? 'Рабочие станции' : 'Устройства',
      icon: 'monitoring',
      primeIcon: 'pi pi-desktop'
    },
    {
      navLabel: 'Топология',
      topbarTitle: 'Топология',
      icon: 'topology',
      primeIcon: 'pi pi-sitemap'
    },
    {
      navLabel: 'Шаблоны',
      topbarTitle: 'Шаблоны мониторинга',
      icon: 'templates',
      primeIcon: 'pi pi-folder-open',
      adminOnly: true
    },
    {
      navLabel: 'События',
      topbarTitle: 'События',
      icon: 'events',
      primeIcon: 'pi pi-bell'
    },
    {
      navLabel: 'Настройка системы',
      topbarTitle: 'Настройка системы',
      icon: 'systemSettings',
      primeIcon: 'pi pi-cog',
      adminOnly: true
    },
    {
      navLabel: 'Пользователи',
      topbarTitle: 'Пользователи',
      icon: 'users',
      primeIcon: 'pi pi-users',
      adminOnly: true
    },
    {
      navLabel: 'Аудит',
      topbarTitle: 'Журнал аудита',
      icon: 'audit',
      primeIcon: 'pi pi-history',
      adminOnly: true
    }
  ] as NavigationItem[]).filter(
    (item) =>
      !environment.hideNetworkScannerFeatures ||
      (item.icon !== 'scan' && item.icon !== 'scanJobs' && item.icon !== 'topology')
  );

  protected readonly userModalOpen = signal(false);
  protected readonly activeMenuItem = signal('Сканирование сетевого оборудования');

  /** Сегмент id в URL `/monitoring/:id` (не список). */
  protected readonly monitoringDeviceIdFromUrl = signal<string | null>(null);

  protected readonly topbarMonitoringDeviceTitle = computed(() => {
    const id = this.monitoringDeviceIdFromUrl();
    if (!id) return '';
    const dev = this.mon.getMonitoredDevice(id);
    if (!dev) return 'Загрузка…';
    const h = dev.hostName?.trim();
    if (h && h !== '-') return h;
    return dev.ip || 'Устройство';
  });

  /** Узкий экран: выезжающее меню */
  protected readonly isNarrow = signal(false);
  /** Десктоп: колонка сайдбара скрыта полностью */
  protected readonly sidebarHidden = signal(false);
  /** Узкая полоса только с иконками */
  protected readonly sidebarCollapsed = signal(false);
  /** Мобильный оверлей открыт */
  protected readonly drawerOpen = signal(false);

  constructor() {
    afterNextRender(() => {
      if (!isPlatformBrowser(this.platformId)) return;
      this.refreshViewport();
      if (typeof localStorage !== 'undefined') {
        const c = localStorage.getItem('netscan.sidebarCollapsed');
        if (c === '1') this.sidebarCollapsed.set(true);
      }
      fromEvent(window, 'resize')
        .pipe(debounceTime(100), takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.refreshViewport());
    });

    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((e) => {
        this.syncActiveMenuFromUrl(e.urlAfterRedirects);
        if (this.isNarrow()) this.drawerOpen.set(false);
      });

    this.syncActiveMenuFromUrl(this.router.url);
  }

  protected logout(): void {
    this.userModalOpen.set(false);
    this.mon.clearSessionState();
    this.auth.logout();
  }

  protected openUserModal(): void {
    this.userModalOpen.set(true);
  }

  protected closeUserModal(): void {
    this.userModalOpen.set(false);
  }

  protected visibleNavigationItems(): NavigationItem[] {
    return this.navigationItems.filter((item) => {
      if (item.icon === 'systemSettings') {
        return this.canOpenSystemSettings();
      }
      return !item.adminOnly || this.canManageUsers();
    });
  }

  /** Подсветка «Мониторинг» на `/monitoring` и на карточке `/monitoring/:id`. */
  protected navActiveMatchOptions(item: NavigationItem): Partial<IsActiveMatchOptions> {
    const base = {
      queryParams: 'ignored' as const,
      fragment: 'ignored' as const,
      matrixParams: 'ignored' as const
    };
    if (item.icon === 'scan' || item.icon === 'topology') {
      return { ...base, paths: 'exact' as const };
    }
    return { ...base, paths: 'subset' as const };
  }

  protected navPath(item: NavigationItem): string {
    switch (item.icon) {
      case 'scan':
        return '/scan';
      case 'scanJobs':
        return '/scan-jobs';
      case 'monitoring':
        return environment.hideNetworkScannerFeatures ? '/workstations' : '/monitoring';
      case 'topology':
        return '/topology';
      case 'templates':
        return '/monitoring-templates';
      case 'events':
        return '/events';
      case 'dashboards':
        return '/dashboards';
      case 'audit':
        return '/audit';
      case 'users':
        return '/users';
      case 'systemSettings':
        return '/system-settings';
    }
  }

  /** Сайдбар на экране (не свёрнута колонка на десктопе или открыт drawer на мобильном) */
  protected sidebarRailVisible(): boolean {
    if (this.isNarrow()) return this.drawerOpen();
    return !this.sidebarHidden();
  }

  /** Десктопная колонка сайдбара (не drawer) */
  protected isDesktopSidebar(): boolean {
    return !this.isNarrow() && !this.sidebarHidden();
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected onTopbarMenuClick(): void {
    if (this.isNarrow()) {
      this.drawerOpen.update((v) => !v);
      return;
    }
    this.sidebarHidden.update((v) => !v);
  }

  protected toggleCollapse(): void {
    this.sidebarCollapsed.update((v) => !v);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem('netscan.sidebarCollapsed', this.sidebarCollapsed() ? '1' : '0');
    }
  }

  protected onNavLinkClick(): void {
    if (this.isNarrow()) this.drawerOpen.set(false);
  }

  protected topbarMenuIcon(): string {
    if (this.isNarrow()) {
      return this.drawerOpen() ? 'pi pi-times' : 'pi pi-bars';
    }
    return 'pi pi-bars';
  }

  protected topbarMenuAria(): string {
    if (this.isNarrow()) {
      return this.drawerOpen() ? 'Закрыть меню' : 'Открыть меню';
    }
    return this.sidebarHidden() ? 'Показать боковую панель' : 'Скрыть боковую панель';
  }

  protected currentUserDisplayName(): string {
    return this.auth.authSession()?.displayName ?? 'User';
  }

  protected currentUserEmail(): string {
    return this.auth.authSession()?.email ?? '-';
  }

  protected currentUserRoles(): AppRole[] {
    return this.auth.authSession()?.roles ?? [];
  }

  protected currentUserRoleLabel(): string {
    const roles = this.auth.authSession()?.roles ?? [];
    if (roles.includes('ADMIN')) return 'Administrator';
    if (roles.includes('OPERATOR')) return 'Operator';
    return 'Viewer';
  }

  protected userInitials(): string {
    return (
      this.currentUserDisplayName()
        .split(' ')
        .filter(Boolean)
        .slice(0, 2)
        .map((v) => v[0]?.toUpperCase() ?? '')
        .join('') || 'NS'
    );
  }

  private refreshViewport(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const narrow = window.matchMedia('(max-width: 900px)').matches;
    this.isNarrow.set(narrow);
    if (!narrow) this.drawerOpen.set(false);
  }

  private canManageUsers(): boolean {
    return this.auth.authSession()?.roles.includes('ADMIN') ?? false;
  }

  private canOpenSystemSettings(): boolean {
    const roles = this.auth.authSession()?.roles ?? [];
    return roles.includes('ADMIN') || roles.includes('OPERATOR');
  }

  private syncActiveMenuFromUrl(url: string): void {
    this.monitoringDeviceIdFromUrl.set(this.parseMonitoringDeviceIdFromUrl(url));

    if (url.includes('/scan-jobs')) {
      this.activeMenuItem.set('Задачи автосканирования');
      return;
    }
    if (url.includes('/audit')) {
      this.activeMenuItem.set('Журнал аудита');
      return;
    }
    if (url.includes('/users')) {
      this.activeMenuItem.set('Пользователи');
      return;
    }
    if (url.includes('/system-settings')) {
      this.activeMenuItem.set('Настройка системы');
      return;
    }
    if (this.pathStartsWithTopology(url)) {
      this.activeMenuItem.set('Топология');
      return;
    }
    if (url.includes('/dashboards')) {
      this.activeMenuItem.set('Дашборды');
      return;
    }
    if (url.includes('/monitoring-templates')) {
      this.activeMenuItem.set('Шаблоны мониторинга');
      return;
    }
    if (url.includes('/workstations')) {
      this.activeMenuItem.set(environment.hideNetworkScannerFeatures ? 'Рабочие станции' : 'Устройства');
      return;
    }
    if (url.includes('/monitoring')) {
      this.activeMenuItem.set('Устройства');
      return;
    }
    if (url.includes('/events')) {
      this.activeMenuItem.set('События');
      return;
    }
    this.activeMenuItem.set('Сканирование сетевого оборудования');
  }

  private pathStartsWithTopology(url: string): boolean {
    const path = url.split('?')[0].split('#')[0];
    return path === '/topology' || path.startsWith('/topology/');
  }

  private parseMonitoringDeviceIdFromUrl(url: string): string | null {
    const path = url.split('?')[0].split('#')[0];
    const segments = path.split('/').filter(Boolean);
    if (segments.length !== 2 || segments[0] !== 'monitoring') {
      return null;
    }
    return segments[1];
  }
}
