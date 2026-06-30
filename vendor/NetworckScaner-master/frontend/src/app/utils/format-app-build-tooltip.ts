export function formatAppBuildTooltip(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '';
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getUTCDate())}.${pad(d.getUTCMonth() + 1)}.${String(d.getUTCFullYear()).slice(-2)} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}

/** Суффикс официальной сборки в version: yyMMddHHmm (см. AppVersionResolver на backend). */
export function formatAppBuildTooltipFromVersionSuffix(version: string): string {
  const match = String(version ?? '').match(/\.(\d{10})$/);
  if (!match) {
    return '';
  }
  const suffix = match[1];
  const yy = suffix.slice(0, 2);
  const month = suffix.slice(2, 4);
  const day = suffix.slice(4, 6);
  const hours = suffix.slice(6, 8);
  const minutes = suffix.slice(8, 10);
  return `${day}.${month}.${yy} ${hours}:${minutes}`;
}

export function resolveAppBuildTooltip(version: string, buildTime: string | null | undefined): string {
  if (buildTime) {
    const fromIso = formatAppBuildTooltip(buildTime);
    if (fromIso) {
      return fromIso;
    }
  }
  return formatAppBuildTooltipFromVersionSuffix(version);
}
