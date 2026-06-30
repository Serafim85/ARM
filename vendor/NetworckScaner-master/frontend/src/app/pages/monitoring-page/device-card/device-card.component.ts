import { Component, ViewEncapsulation, computed, effect, inject, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { BadgeModule } from 'primeng/badge';
import { TabsModule } from 'primeng/tabs';
import { DeviceScanResult } from '../../../models';
import { MonitoringService } from '../../../services/monitoring.service';
import { DeviceCardTabSegment, isDeviceCardTabSegment } from './device-card-tab.model';

@Component({
  selector: 'app-device-card',
  standalone: true,
  imports: [TabsModule, BadgeModule, RouterLink, RouterOutlet],
  templateUrl: './device-card.component.html',
  styleUrl: './device-card.component.css',
  encapsulation: ViewEncapsulation.None,
})
export class DeviceCardComponent {
  /** Класс на корне p-tabpanels (pt), чтобы переопределить padding/фон поверх темы */
  protected readonly deviceCardTabPanelsPt = { root: { class: 'device-card-tabpanels' } };

  protected readonly ms = inject(MonitoringService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly deviceId = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('id')!)),
    { initialValue: this.route.snapshot.params['id'] as string },
  );

  protected readonly device = computed<DeviceScanResult | null>(() => {
    const id = this.deviceId();
    return this.ms.getMonitoredDevice(id);
  });

  /** Шапка «Загрузка…» + прелоадер: список грузится или подтягиваем одно устройство по id. */
  protected readonly showDevicePageSkeleton = computed(() => {
    if (this.device() != null) return false;
    const id = this.deviceId();
    if (!id) return false;
    if (this.ms.devicesLoading()) return true;
    return this.ms.isMonitoredDeviceDetailFetching(id);
  });

  protected readonly activeTabSegment = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(() => this.resolveTabSegmentFromRoute()),
      startWith(this.resolveTabSegmentFromRoute()),
    ),
    { initialValue: this.resolveTabSegmentFromRoute() },
  );

  /** Смена устройства в URL: сброс на вкладку «Общая информация» (не трогаем глубокие ссылки при первом заходе). */
  private lastRouteDeviceIdForTab: string | null = null;

  protected readonly openEventsCount = computed(() => {
    const device = this.device();
    if (!device) return 0;
    return this.ms.deviceOpenEventCount()[device.id] ?? 0;
  });

  /** Заголовок страницы (имя хоста или IP). */
  protected readonly pageTitle = computed(() => {
    const dev = this.device();
    if (!dev) return '';
    const h = dev.hostName?.trim();
    if (h && h !== '-') return h;
    return dev.ip || 'Устройство';
  });

  /** Подзаголовок в стиле scan-page-lead. */
  protected readonly pageLead = computed(() => {
    const dev = this.device();
    if (!dev) return '';
    const parts: string[] = [];
    if (dev.ip?.trim()) parts.push(dev.ip.trim());
    const vm = [dev.vendor?.trim(), dev.model?.trim()].filter(Boolean).join(' ');
    if (vm) parts.push(vm);
    return parts.length ? parts.join(' · ') : 'Параметры и состояние на мониторинге.';
  });

  constructor() {
    effect(() => {
      const id = this.deviceId();
      const dev = this.ms.getMonitoredDevice(id);

      untracked(() => {
        if (!dev) {
          this.ms.ensureMonitoredDeviceLoaded(id);
        }

        if (this.lastRouteDeviceIdForTab !== null && this.lastRouteDeviceIdForTab !== id) {
          void this.router.navigate(['info'], { relativeTo: this.route });
        }
        this.lastRouteDeviceIdForTab = id;
      });

      if (!dev) {
        return;
      }

      untracked(() => {
        this.ms.loadMonitoringDetails(dev);
        this.ms.loadMonitoredDeviceMeta(dev.id);
        this.ms.loadDeviceDiscoveryState(dev.id);
        this.ms.loadPortConfiguration(dev);
        this.ms.loadDeviceOpenEventCount(dev);
      });
    });
  }

  protected onTabChange(value: string | number | undefined): void {
    const seg = String(value ?? 'info');
    if (!isDeviceCardTabSegment(seg)) return;
    if (seg === this.activeTabSegment()) return;
    void this.router.navigate([seg], { relativeTo: this.route });
  }

  private resolveTabSegmentFromRoute(): DeviceCardTabSegment {
    const path = this.route.snapshot.firstChild?.routeConfig?.path
      ?? this.route.firstChild?.routeConfig?.path;
    return path && isDeviceCardTabSegment(path) ? path : 'info';
  }
}

