import { HttpClient } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { DeviceOptionSelectComponent } from '../../../components/device-option-select/device-option-select.component';
import { API_BASE_URL } from '../../../api-config';
import type { WidgetFieldRecord, WidgetFieldUpsert } from '../../../models';
import {
  migrateLegacyHostRefsToDeviceIds,
  parseProblemsWidgetFields,
} from '../dashboard-problems-widget/problems-widget-config';
import type { WidgetFieldsEditorApi } from './widget-editor.types';

type MonitoringDeviceRow = {
  id: number;
  name: string;
  hostName?: string | null;
  ip: string;
  macAddress?: string | null;
  tags?: string[] | null;
};

type MonitoringDevicePageResponse = {
  content: MonitoringDeviceRow[];
};

type DeviceMultiSelectOption = {
  id: number;
  label: string;
};

type TagMultiSelectOption = {
  value: string;
  label: string;
};

@Component({
  selector: 'app-problems-widget-fields',
  standalone: true,
  imports: [
    FormsModule,
    SelectModule,
    CheckboxModule,
    InputNumberModule,
    InputTextModule,
    MultiSelectModule,
    DeviceOptionSelectComponent,
  ],
  templateUrl: './problems-widget-fields.component.html',
  styleUrl: './widget-fields-editor.component.css',
})
export class ProblemsWidgetFieldsComponent implements WidgetFieldsEditorApi {
  private static readonly MULTISELECT_CHROME_PX = 56;
  private static readonly FALLBACK_VISIBLE_HOST_CHIPS = 1;
  private static readonly FALLBACK_VISIBLE_TAG_CHIPS = 3;

  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);

  readonly hostsMultiSelectHost = viewChild<ElementRef<HTMLElement>>('hostsMultiSelectHost');
  readonly tagsMultiSelectHost = viewChild<ElementRef<HTMLElement>>('tagsMultiSelectHost');
  protected readonly maxVisibleHostChips = signal(ProblemsWidgetFieldsComponent.FALLBACK_VISIBLE_HOST_CHIPS);
  protected readonly maxVisibleTagChips = signal(ProblemsWidgetFieldsComponent.FALLBACK_VISIBLE_TAG_CHIPS);

  protected readonly showOptions = [
    { label: 'Актуальные и недавние', value: 'RECENT' },
    { label: 'Только активные', value: 'PROBLEMS' },
    { label: 'История', value: 'HISTORY' },
  ];
  protected readonly sortByOptions = [
    { label: 'Время', value: 'TIME' },
    { label: 'Серьёзность', value: 'SEVERITY' },
    { label: 'Имя проблемы', value: 'PROBLEM' },
    { label: 'Хост', value: 'HOST' },
  ];
  protected readonly sortOrderOptions = [
    { label: 'По убыванию', value: 'DESC' },
    { label: 'По возрастанию', value: 'ASC' },
  ];

  protected readonly problemHint =
    'Оставляет события, у которых имя метрики содержит указанную подстроку (без учёта регистра).';
  protected readonly hostsHint =
    'События только с выбранных хостов. Пусто — со всех хостов.';
  protected readonly tagsHint = 'События хостов с выбранными тегами. Пусто — без фильтра по тегам.';

  protected show = 'RECENT';
  protected showLines = 10;
  protected showTimeline = true;
  protected showSuppressed = false;
  protected highlightRow = false;
  protected sortBy = 'TIME';
  protected sortOrder = 'DESC';
  protected problem = '';
  protected selectedDeviceIds: number[] = [];
  protected selectedDeviceTags: string[] = [];
  protected deviceOptions: DeviceMultiSelectOption[] = [];
  protected tagOptions: TagMultiSelectOption[] = [];
  protected devicesLoading = false;

  private loadedDevices: MonitoringDeviceRow[] = [];
  private pendingLegacyHostRefs = '';

  constructor() {
    this.loadMonitoringDevices();
    afterNextRender(() => {
      this.bindMultiSelectLayoutObserver(
        this.hostsMultiSelectHost(),
        (width) => this.updateMaxVisibleChips(width, 'host')
      );
      this.bindMultiSelectLayoutObserver(
        this.tagsMultiSelectHost(),
        (width) => this.updateMaxVisibleChips(width, 'tag')
      );
    });
  }

  patchFromFields(fields: WidgetFieldRecord[]): void {
    const map = new Map(fields.map((x) => [x.name, x]));
    const parsed = parseProblemsWidgetFields(fields);
    this.show = parsed.show;
    this.showLines = parsed.showLines;
    this.showTimeline = parsed.showTimeline;
    this.showSuppressed = parsed.showSuppressed;
    this.highlightRow = parsed.highlightRow;
    this.sortBy = parsed.sortBy;
    this.sortOrder = parsed.sortOrder;
    this.problem = parsed.problem;
    this.selectedDeviceIds = [...parsed.deviceIds];
    this.selectedDeviceTags = [...parsed.deviceTags];

    const legacyHostRefs = this.pickString(map, 'host_refs', '');
    if (this.selectedDeviceIds.length === 0 && legacyHostRefs.trim()) {
      this.pendingLegacyHostRefs = legacyHostRefs;
      this.applyLegacyHostRefsMigration();
    } else {
      this.pendingLegacyHostRefs = '';
    }
  }

  buildFields(): WidgetFieldUpsert[] {
    const out: WidgetFieldUpsert[] = [
      { name: 'show', valueInt: 0, valueStr: this.show },
      { name: 'show_lines', valueInt: this.normalizeLines(this.showLines), valueStr: '' },
      this.boolField('show_timeline', this.showTimeline),
      this.boolField('show_suppressed', this.showSuppressed),
      this.boolField('highlight_row', this.highlightRow),
      { name: 'sort_by', valueInt: 0, valueStr: this.sortBy },
      { name: 'sort_order', valueInt: 0, valueStr: this.sortOrder },
    ];
    if (this.problem.trim()) {
      out.push({ name: 'problem', valueInt: 0, valueStr: this.problem.trim() });
    }
    if (this.selectedDeviceIds.length > 0) {
      out.push({
        name: 'device_ids',
        valueInt: 0,
        valueStr: JSON.stringify(this.selectedDeviceIds),
      });
    }
    if (this.selectedDeviceTags.length > 0) {
      out.push({
        name: 'device_tags',
        valueInt: 0,
        valueStr: JSON.stringify(this.selectedDeviceTags),
      });
    }
    return out;
  }

  private loadMonitoringDevices(): void {
    this.devicesLoading = true;
    this.http
      .get<MonitoringDevicePageResponse>(`${this.apiBaseUrl}/api/monitoring`, {
        params: { page: '0', size: '500', sortField: 'name', sortOrder: 'asc' },
      })
      .subscribe({
        next: (response) => {
          this.loadedDevices = response.content ?? [];
          this.deviceOptions = this.loadedDevices.map((row) => ({
            id: row.id,
            label: formatDeviceSelectLabel(row),
          }));
          this.tagOptions = this.buildTagOptions(this.loadedDevices);
          this.devicesLoading = false;
          this.applyLegacyHostRefsMigration();
          this.refreshChipLimits('host');
          this.refreshChipLimits('tag');
        },
        error: () => {
          this.loadedDevices = [];
          this.deviceOptions = [];
          this.tagOptions = [];
          this.devicesLoading = false;
        },
      });
  }

  private buildTagOptions(devices: MonitoringDeviceRow[]): TagMultiSelectOption[] {
    const tags = new Set<string>();
    for (const device of devices) {
      for (const tag of device.tags ?? []) {
        const t = tag?.trim();
        if (t) tags.add(t);
      }
    }
    return [...tags]
      .sort((a, b) => a.localeCompare(b, 'ru'))
      .map((value) => ({ value, label: value }));
  }

  private applyLegacyHostRefsMigration(): void {
    if (!this.pendingLegacyHostRefs.trim() || this.loadedDevices.length === 0) {
      return;
    }
    const migrated = migrateLegacyHostRefsToDeviceIds(this.pendingLegacyHostRefs, this.loadedDevices);
    if (migrated.length > 0) {
      this.selectedDeviceIds = migrated;
      this.refreshChipLimits('host');
    }
    this.pendingLegacyHostRefs = '';
  }

  protected hostsSelectedSummaryLabel(): string {
    const count = this.selectedDeviceIds.length;
    return `Выбрано ${count} ${pluralizeRu(count, ['хост', 'хоста', 'хостов'])}`;
  }

  protected tagsSelectedSummaryLabel(): string {
    const count = this.selectedDeviceTags.length;
    return `Выбрано ${count} ${pluralizeRu(count, ['тег', 'тега', 'тегов'])}`;
  }

  protected refreshChipLimits(kind: 'host' | 'tag'): void {
    const hostRef = kind === 'host' ? this.hostsMultiSelectHost() : this.tagsMultiSelectHost();
    this.updateMaxVisibleChips(hostRef?.nativeElement.clientWidth ?? 0, kind);
  }

  private normalizeLines(value: number | null | undefined): number {
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      return 10;
    }
    return Math.max(1, Math.floor(value));
  }

  private boolField(name: string, value: boolean): WidgetFieldUpsert {
    return { name, valueInt: value ? 1 : 0, valueStr: '' };
  }

  private pickString(map: Map<string, WidgetFieldRecord>, name: string, fallback: string): string {
    const value = map.get(name);
    if (!value || !value.valueStr) {
      return fallback;
    }
    return value.valueStr;
  }

  private bindMultiSelectLayoutObserver(
    hostRef: ElementRef<HTMLElement> | undefined,
    update: (width: number) => void
  ): void {
    const host = hostRef?.nativeElement;
    if (!host || typeof ResizeObserver === 'undefined') {
      return;
    }

    const runUpdate = () => update(host.clientWidth);
    runUpdate();

    const observer = new ResizeObserver(() => runUpdate());
    observer.observe(host);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  private updateMaxVisibleChips(width: number, kind: 'host' | 'tag'): void {
    const fallback =
      kind === 'host'
        ? ProblemsWidgetFieldsComponent.FALLBACK_VISIBLE_HOST_CHIPS
        : ProblemsWidgetFieldsComponent.FALLBACK_VISIBLE_TAG_CHIPS;
    const signalRef = kind === 'host' ? this.maxVisibleHostChips : this.maxVisibleTagChips;
    const hostRef = kind === 'host' ? this.hostsMultiSelectHost() : this.tagsMultiSelectHost();

    if (width <= 0) {
      if (signalRef() < fallback) {
        signalRef.set(fallback);
      }
      requestAnimationFrame(() => {
        const host = hostRef?.nativeElement;
        if (host) {
          this.updateMaxVisibleChips(host.clientWidth, kind);
        }
      });
      return;
    }

    const available = Math.max(0, width - ProblemsWidgetFieldsComponent.MULTISELECT_CHROME_PX);
    const labels = this.selectedLabelsForKind(kind);
    const next =
      labels.length > 0
        ? countLabelsThatFit(labels, available)
        : Math.max(1, Math.floor(available / estimateChipWidthPx('placeholder')));
    if (signalRef() !== next) {
      signalRef.set(next);
    }
  }

  private selectedLabelsForKind(kind: 'host' | 'tag'): string[] {
    if (kind === 'host') {
      const byId = new Map(this.deviceOptions.map((option) => [option.id, option.label]));
      return this.selectedDeviceIds
        .map((id) => byId.get(id) ?? '')
        .filter((label) => label.length > 0);
    }
    return this.selectedDeviceTags.map((tag) => tag.trim()).filter((tag) => tag.length > 0);
  }
}

function pluralizeRu(count: number, forms: [string, string, string]): string {
  const n = Math.abs(count) % 100;
  const n1 = n % 10;
  if (n > 10 && n < 20) {
    return forms[2];
  }
  if (n1 === 1) {
    return forms[0];
  }
  if (n1 >= 2 && n1 <= 4) {
    return forms[1];
  }
  return forms[2];
}

function estimateChipWidthPx(label: string): number {
  const text = label.trim();
  return Math.min(380, Math.max(56, Math.ceil(text.length * 8.5) + 40));
}

function countLabelsThatFit(labels: string[], availableWidth: number): number {
  let used = 0;
  let count = 0;
  for (const label of labels) {
    const chipWidth = estimateChipWidthPx(label);
    if (count > 0 && used + chipWidth > availableWidth) {
      break;
    }
    used += chipWidth;
    count++;
  }
  return Math.max(1, count);
}

function formatDeviceSelectLabel(device: MonitoringDeviceRow): string {
  const host = device.hostName?.trim() || device.ip?.trim() || '—';
  const ip = device.ip?.trim() || '—';
  const mac = device.macAddress?.trim();
  const parts = [host, ip];
  if (mac && mac !== '-') {
    parts.push(mac);
  }
  return parts.join(' · ');
}
