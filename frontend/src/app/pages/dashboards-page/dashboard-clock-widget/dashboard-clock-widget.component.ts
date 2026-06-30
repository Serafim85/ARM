import { NgStyle } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import type { EChartsOption } from 'echarts';
import { NgxEchartsDirective } from 'ngx-echarts';
import { Subscription } from 'rxjs';
import type { WidgetFieldRecord } from '../../../models';
import { DashboardsService } from '../../../services/dashboards.service';
import { buildAnalogClockEchartsOption } from './analog-clock-echarts';
import {
  buildAnalogGaugeValues,
  buildClockDisplay,
  defaultServerResyncIntervalSeconds,
  isValidIanaTimeZone,
  parseClockWidgetFields,
  parseHexBackgroundColor,
} from './clock-widget-config';

@Component({
  selector: 'app-dashboard-clock-widget',
  standalone: true,
  imports: [NgStyle, NgxEchartsDirective],
  templateUrl: './dashboard-clock-widget.component.html',
  styleUrl: './dashboard-clock-widget.component.css',
})
export class DashboardClockWidgetComponent {
  private readonly dashboards = inject(DashboardsService);

  readonly fields = input.required<WidgetFieldRecord[]>();
  readonly refreshIntervalSeconds = input<number | null>(null);

  protected readonly config = computed(() => parseClockWidgetFields(this.fields()));

  private readonly serverOffsetMs = signal<number | null>(null);
  protected readonly serverSyncFailed = signal(false);

  /** Wall-clock instant for LOCAL; for SERVER added offset applied inside timer effect. */
  private readonly nowMs = signal(Date.now());

  protected readonly hostPlaceholder = computed(() => this.config().timeType === 'HOST');

  protected readonly timeZoneInvalid = computed(() => {
    const c = this.config();
    return !isValidIanaTimeZone(c.timeZone);
  });

  protected readonly serverWaiting = computed(() => {
    const c = this.config();
    return c.timeType === 'SERVER' && this.serverOffsetMs() === null && !this.serverSyncFailed();
  });

  protected readonly backgroundStyle = computed(() => {
    const hex = parseHexBackgroundColor(this.config());
    return hex ? { background: hex } : null;
  });

  /** Фон под циферблатом (как в демо ECharts — белый; кастомный цвет из полей виджета перекрывает). */
  protected readonly analogChartHostStyle = computed(() => ({
    background: parseHexBackgroundColor(this.config()) ?? '#ffffff',
  }));

  protected readonly digitalLines = computed(() => {
    if (
      this.hostPlaceholder() ||
      this.timeZoneInvalid() ||
      this.serverWaiting() ||
      this.serverSyncFailed()
    ) {
      return { dateLine: null as string | null, timeLine: null as string | null, zoneLine: null as string | null };
    }
    return buildClockDisplay(this.nowMs(), this.config());
  });

  protected readonly analogEchartsOption = computed<EChartsOption | null>(() => {
    if (
      this.hostPlaceholder() ||
      this.timeZoneInvalid() ||
      this.serverWaiting() ||
      this.serverSyncFailed()
    ) {
      return null;
    }
    const c = this.config();
    if (c.clockType !== 'ANALOG') {
      return null;
    }
    const values = buildAnalogGaugeValues(this.nowMs(), c);
    return buildAnalogClockEchartsOption(values, c.showSeconds);
  });

  protected readonly analogExtraLines = computed(() => {
    if (
      this.config().clockType !== 'ANALOG' ||
      this.hostPlaceholder() ||
      this.timeZoneInvalid() ||
      this.serverWaiting() ||
      this.serverSyncFailed()
    ) {
      return { dateLine: null as string | null, timeLine: null as string | null, zoneLine: null as string | null };
    }
    const c = this.config();
    const needDate = c.showDate;
    const needTime = c.showTime;
    const needZone = c.showTimeZone;
    if (!needDate && !needTime && !needZone) {
      return { dateLine: null, timeLine: null, zoneLine: null };
    }
    return buildClockDisplay(this.nowMs(), {
      ...c,
      showDate: needDate,
      showTime: needTime,
      showTimeZone: needZone,
    });
  });

  protected readonly digitalEmpty = computed(() => {
    if (this.config().clockType !== 'DIGITAL') {
      return false;
    }
    const d = this.digitalLines();
    return !d.dateLine && !d.timeLine && !d.zoneLine;
  });

  constructor() {
    effect((onCleanup) => {
      const c = this.config();
      if (c.timeType !== 'SERVER') {
        this.serverOffsetMs.set(null);
        this.serverSyncFailed.set(false);
        return;
      }

      let cancelled = false;
      let active: Subscription | null = null;
      const runSync = () => {
        active?.unsubscribe();
        active = this.dashboards.getServerTime().subscribe({
          next: (r) => {
            if (!cancelled) {
              this.serverOffsetMs.set(r.epochMillis - Date.now());
              this.serverSyncFailed.set(false);
            }
          },
          error: () => {
            if (!cancelled) {
              this.serverSyncFailed.set(true);
              this.serverOffsetMs.set(null);
            }
          },
        });
      };

      runSync();
      const periodMs = defaultServerResyncIntervalSeconds(this.refreshIntervalSeconds()) * 1000;
      const id = window.setInterval(runSync, periodMs);
      onCleanup(() => {
        cancelled = true;
        active?.unsubscribe();
        window.clearInterval(id);
      });
    });

    effect((onCleanup) => {
      const c = this.config();
      if (c.timeType === 'HOST') {
        return;
      }
      if (c.timeType === 'SERVER' && this.serverOffsetMs() === null) {
        return;
      }

      const offset = c.timeType === 'SERVER' ? (this.serverOffsetMs() ?? 0) : 0;
      const tick = () => {
        this.nowMs.set(Date.now() + offset);
      };
      tick();

      const everySecond = clockNeedsEverySecondTick(c);
      const id = window.setInterval(tick, everySecond ? 1000 : 60_000);
      onCleanup(() => window.clearInterval(id));
    });
  }

}

function clockNeedsEverySecondTick(c: ReturnType<typeof parseClockWidgetFields>): boolean {
  if (c.clockType === 'ANALOG') {
    return c.showSeconds;
  }
  if (c.showTime && c.showSeconds) {
    return true;
  }
  return false;
}
