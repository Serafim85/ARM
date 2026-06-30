import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { DiscoveryMethod } from '../../models';
import { ScanService } from '../../services/scan.service';
import { isSnmpMethod, probeUsesPort, probeUsesProfileSettings } from '../../utils/scan-probe.util';
import { DeviceOptionSelectComponent } from '../device-option-select/device-option-select.component';

@Component({
  selector: 'app-discovery-probes-form',
  standalone: true,
  imports: [
    FormsModule,
    InputTextModule,
    InputNumberModule,
    MultiSelectModule,
    SelectModule,
    ButtonModule,
    DeviceOptionSelectComponent,
  ],
  templateUrl: './discovery-probes-form.component.html',
  styleUrl: './discovery-probes-form.component.css',
})
export class DiscoveryProbesFormComponent {
  private static readonly METHOD_CHIP_AVG_WIDTH_PX = 88;
  private static readonly MULTISELECT_CHROME_PX = 52;
  /** Пока контейнер ещё не измерен (диалог), не схлопывать до одного чипа. */
  private static readonly FALLBACK_VISIBLE_CHIPS = 3;

  protected readonly scan = inject(ScanService);
  private readonly destroyRef = inject(DestroyRef);

  readonly showSubnet = input(true);
  readonly methodsMultiSelectHost = viewChild<ElementRef<HTMLElement>>('methodsMultiSelectHost');
  readonly maxVisibleMethodChips = signal(this.scan.discoveryMethods.length);

  constructor() {
    this.scan.loadAccessProfiles();
    afterNextRender(() => this.bindMethodChipsLayoutObserver());
  }

  protected readonly authProtocolOptions = [
    { label: 'MD5', value: 'MD5' },
    { label: 'SHA', value: 'SHA' },
  ];

  protected readonly privacyProtocolOptions = [
    { label: 'DES', value: 'DES' },
    { label: 'AES', value: 'AES' },
  ];

  protected usesAccessProfile(): boolean {
    return this.scan.accessProfileId() != null;
  }

  protected probeUsesPort(method: DiscoveryMethod): boolean {
    return probeUsesPort(method);
  }

  protected isSnmpMethod(method: DiscoveryMethod): boolean {
    return isSnmpMethod(method);
  }

  protected probeHasFields(method: DiscoveryMethod): boolean {
    if (this.scan.usesProfileForProbe(method)) {
      return false;
    }
    if (this.scan.usesManualProbeConfig(method)) {
      return probeUsesPort(method) || isSnmpMethod(method);
    }
    if (this.usesAccessProfile()) {
      return probeUsesPort(method);
    }
    return probeUsesPort(method) || isSnmpMethod(method);
  }

  protected probeShowsPort(method: DiscoveryMethod): boolean {
    return probeUsesPort(method) && !this.scan.usesProfileForProbe(method);
  }

  protected showsProfileHint(method: DiscoveryMethod): boolean {
    return this.scan.usesProfileForProbe(method);
  }

  protected usesManualProbeConfig(method: DiscoveryMethod): boolean {
    return this.scan.usesManualProbeConfig(method);
  }

  protected usesInlineSnmpFields(method: DiscoveryMethod): boolean {
    return !this.usesAccessProfile() || this.usesManualProbeConfig(method);
  }

  protected getParameterlessHint(method: DiscoveryMethod): string {
    return 'Дополнительные параметры не требуются.';
  }

  protected onMethodsChange(value: DiscoveryMethod[] | null | undefined): void {
    const methods = Array.isArray(value) ? value : [];
    if (methods.length === 0 && !this.scan.canRemoveProbe()) {
      return;
    }
    this.scan.setSelectedMethods(methods);
  }

  protected resetMethods(): void {
    this.scan.resetDiscoveryMethods();
  }

  protected removeProbe(method: DiscoveryMethod): void {
    this.scan.removeProbe(method);
  }

  protected onProbePortChange(method: DiscoveryMethod, value: number | null): void {
    this.scan.updateProbe(method, { port: value ?? undefined });
  }

  protected onProbeFieldChange(
    method: DiscoveryMethod,
    field: 'community' | 'securityUsername' | 'authProtocol' | 'authPassword' | 'privacyProtocol' | 'privacyPassword',
    value: string
  ): void {
    this.scan.updateProbe(method, { [field]: value });
  }

  protected onAuthProtocolChange(method: DiscoveryMethod, value: string | number | null): void {
    if (value != null) {
      this.onProbeFieldChange(method, 'authProtocol', String(value));
    }
  }

  protected onPrivacyProtocolChange(method: DiscoveryMethod, value: string | number | null): void {
    if (value != null) {
      this.onProbeFieldChange(method, 'privacyProtocol', String(value));
    }
  }

  private bindMethodChipsLayoutObserver(): void {
    const host = this.methodsMultiSelectHost()?.nativeElement;
    if (!host || typeof ResizeObserver === 'undefined') {
      return;
    }

    const update = () => this.updateMaxVisibleMethodChips(host.clientWidth);
    update();

    const observer = new ResizeObserver(() => update());
    observer.observe(host);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  private updateMaxVisibleMethodChips(width: number): void {
    if (width <= 0) {
      if (this.maxVisibleMethodChips() < DiscoveryProbesFormComponent.FALLBACK_VISIBLE_CHIPS) {
        this.maxVisibleMethodChips.set(DiscoveryProbesFormComponent.FALLBACK_VISIBLE_CHIPS);
      }
      requestAnimationFrame(() => {
        const host = this.methodsMultiSelectHost()?.nativeElement;
        if (host) {
          this.updateMaxVisibleMethodChips(host.clientWidth);
        }
      });
      return;
    }

    const available = Math.max(0, width - DiscoveryProbesFormComponent.MULTISELECT_CHROME_PX);
    const next = Math.min(
      this.scan.discoveryMethods.length,
      Math.max(1, Math.floor(available / DiscoveryProbesFormComponent.METHOD_CHIP_AVG_WIDTH_PX))
    );

    if (this.maxVisibleMethodChips() !== next) {
      this.maxVisibleMethodChips.set(next);
    }
  }
}
