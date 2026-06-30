export function formatMetricLabel(metricName: string): string {
  const withoutParams = metricName.trim().replace(/\[[^\]]*]/g, '');
  return withoutParams
    .replace(/[._]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function resolveMetricDisplayLabel(
  metricName: string,
  metricDisplayName?: string | null
): string {
  const display = metricDisplayName?.trim();
  if (display) {
    return display;
  }
  return formatMetricLabel(metricName);
}
