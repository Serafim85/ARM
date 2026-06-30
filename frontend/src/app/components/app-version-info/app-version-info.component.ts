import { Component, computed, inject, input, signal } from '@angular/core';
import { TooltipModule } from 'primeng/tooltip';
import { SystemSettingsService } from '../../services/system-settings.service';
import { resolveAppBuildTooltip } from '../../utils/format-app-build-tooltip';

@Component({
  selector: 'app-version-info',
  standalone: true,
  imports: [TooltipModule],
  templateUrl: './app-version-info.component.html',
  styleUrl: './app-version-info.component.css',
  host: {
    '[class.app-version-info--compact-host]': 'compact()',
  },
})
export class AppVersionInfoComponent {
  private readonly settingsService = inject(SystemSettingsService);

  readonly compact = input(false);

  protected readonly appVersion = signal<string | null>(null);
  protected readonly appBuildTime = signal<string | null>(null);
  protected readonly copyrightYear = new Date().getFullYear();

  protected readonly displayVersion = computed(() => this.appVersion() ?? 'UNKNOWN');

  protected readonly buildTimeTooltip = computed(() =>
    resolveAppBuildTooltip(this.displayVersion(), this.appBuildTime()),
  );

  protected readonly compactTooltip = computed(() => {
    const version = this.displayVersion();
    return `© Wellink, ${this.copyrightYear} | v.${version}`;
  });

  constructor() {
    this.settingsService.getAppConfig().subscribe({
      next: (config) => {
        this.appVersion.set(config?.version ?? null);
        this.appBuildTime.set(config?.buildTime ?? null);
      },
      error: () => {
        this.appVersion.set(null);
        this.appBuildTime.set(null);
      },
    });
  }
}
