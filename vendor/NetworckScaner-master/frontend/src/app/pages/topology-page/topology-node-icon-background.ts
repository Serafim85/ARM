import type { MonitoringHealthStatus } from '../../models';
import { topologyNodeKindCanvasSvg, topologyNodeKindInnerSvg } from './topology-node-kind-icons';

const TOPOLOGY_NODE_ICON_RASTER_SIZE = 66;
const topologyNodeIconRasterCache = new Map<string, string>();
const topologyNodeIconRasterPending = new Set<string>();
const topologyNodeIconRasterListeners = new Set<() => void>();

function svgDataUrl(svg: string): string {
  return `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`;
}

function notifyTopologyNodeIconRasterReady(): void {
  for (const listener of topologyNodeIconRasterListeners) {
    listener();
  }
}

function rasterizeSvgToPng(svg: string, sizePx: number): Promise<string> {
  if (typeof Image === 'undefined' || typeof document === 'undefined') {
    return Promise.reject(new Error('Rasterization is only available in browser.'));
  }
  return new Promise<string>((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = sizePx;
      canvas.height = sizePx;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error('Canvas 2D context is unavailable.'));
        return;
      }
      ctx.clearRect(0, 0, sizePx, sizePx);
      ctx.drawImage(img, 0, 0, sizePx, sizePx);
      resolve(canvas.toDataURL('image/png'));
    };
    img.onerror = () => reject(new Error('Failed to load SVG icon.'));
    img.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  });
}

function getRasterizedBackgroundImage(key: string, svg: string): string {
  const cached = topologyNodeIconRasterCache.get(key);
  if (cached) return `url("${cached}")`;

  if (
    typeof window !== 'undefined' &&
    typeof document !== 'undefined' &&
    typeof Image !== 'undefined' &&
    !topologyNodeIconRasterPending.has(key)
  ) {
    topologyNodeIconRasterPending.add(key);
    void rasterizeSvgToPng(svg, TOPOLOGY_NODE_ICON_RASTER_SIZE)
      .then((png) => {
        topologyNodeIconRasterCache.set(key, png);
        topologyNodeIconRasterPending.delete(key);
        notifyTopologyNodeIconRasterReady();
      })
      .catch(() => {
        topologyNodeIconRasterPending.delete(key);
      });
  }

  // Фолбэк для первого кадра/SSR: SVG может "прыгать" при wheel zoom, но будет заменён на PNG сразу после растеризации.
  return svgDataUrl(svg);
}

export function subscribeTopologyNodeIconRasterReady(listener: () => void): () => void {
  topologyNodeIconRasterListeners.add(listener);
  return () => topologyNodeIconRasterListeners.delete(listener);
}

/**
 * Контуры треугольника с «!» — как в PrimeIcons `pi-exclamation-triangle`
 * (`node_modules/primeicons/raw-svg/exclamation-triangle.svg`), для слоя Cytoscape.
 */
function primeExclamationTriangleSvg(fill: string): string {
  const g = `<g fill="${fill}"><path fill-rule="evenodd" d="M20,18.75H4a.76.76,0,0,1-.65-.37.77.77,0,0,1,0-.75l8-14a.78.78,0,0,1,1.3,0l8,14a.77.77,0,0,1,0,.75A.76.76,0,0,1,20,18.75ZM5.29,17.25H18.71L12,5.51Z"/><path d="M12,13.25a.76.76,0,0,1-.75-.75V9a.75.75,0,0,1,1.5,0v3.5A.76.76,0,0,1,12,13.25Z"/><path d="M12,16.25a.76.76,0,0,1-.75-.75V15a.75.75,0,0,1,1.5,0v.5A.76.76,0,0,1,12,16.25Z"/></g>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">${g}</svg>`;
}

function composedNodeAndWarningSvg(kind: unknown, warningFill: string): string {
  const deviceInner = topologyNodeKindInnerSvg(kind);
  const warning = primeExclamationTriangleSvg(warningFill);
  // Значок предупреждения визуально лежит на внутренней стороне левого нижнего угла рамки узла.
  // SVG 66×66: иконка занимает 48×48 (translate 9 9, scale 2), бейдж — у левого нижнего угла.
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 66 66"><g fill="none" stroke="#334155" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" transform="translate(9 9) scale(2)">${deviceInner}</g><g transform="translate(0.5 28) scale(0.6)">${warning}</g></svg>`;
}

/**
 * Фон узла топологии: иконка типа устройства + при WARN/CRITICAL — бейдж предупреждения.
 * Cytoscape известен "скачком" SVG background-image во время wheel zoom, поэтому
 * SVG служит только источником: в браузере он один раз растеризуется в PNG 66×66.
 */
export function topologyNodeIconBackgroundStyle(ele: {
  data: (k: string) => unknown;
}): {
  backgroundImage: string;
} {
  const kind = ele.data('nodeKind');
  const health = ele.data('deviceHealthStatus') as MonitoringHealthStatus | undefined | null;
  if (health !== 'WARN' && health !== 'CRITICAL') {
    const svg = topologyNodeKindCanvasSvg(kind);
    return { backgroundImage: getRasterizedBackgroundImage(`base:${String(kind)}`, svg) };
  }
  const fill = health === 'CRITICAL' ? '#dc2626' : '#d97706';
  const svg = composedNodeAndWarningSvg(kind, fill);
  return {
    backgroundImage: getRasterizedBackgroundImage(`health:${String(kind)}:${health}:${fill}`, svg),
  };
}
