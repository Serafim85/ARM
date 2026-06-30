import type { TopologyNodeKind } from '../../models';

function svgDataUrl(svg: string): string {
  return `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`;
}

/**
 * Базовый SVG 33×33 для узла Cytoscape. Внутренняя геометрия 24×24, с полями по 4.5.
 */
function svgIcon(inner: string): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 33 33" fill="none" stroke="#334155" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><g transform="translate(4.5 4.5)">${inner}</g></svg>`;
}

/**
 * Полноразмерный SVG 66×66 для растеризации в PNG без потери отступов.
 * Иконка занимает 48×48 и остаётся по центру узла.
 */
function nodeCanvasSvgIcon(inner: string): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 66 66"><g fill="none" stroke="#334155" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" transform="translate(9 9) scale(2)">${inner}</g></svg>`;
}

const TOPOLOGY_NODE_KIND_INNER: Record<TopologyNodeKind, string> = {
  NETWORK:
    '<circle cx="5" cy="12" r="2"/><circle cx="12" cy="7" r="2"/><circle cx="19" cy="12" r="2"/><circle cx="12" cy="17" r="2"/><path d="M6.5 11l4-3M13.5 8l4 3M6.5 13l4 3M13.5 16l4-3M12 9v6"/>',
  RACK:
    '<rect x="4" y="4" width="16" height="4" rx="1"/><rect x="4" y="10" width="16" height="4" rx="1"/><rect x="4" y="16" width="16" height="4" rx="1"/><line x1="8" y1="6" x2="10" y2="6"/><line x1="8" y1="12" x2="10" y2="12"/><line x1="8" y1="18" x2="10" y2="18"/>',
  SERVER:
    '<rect x="4" y="5" width="16" height="14" rx="2"/><circle cx="8.5" cy="12" r="1.25"/><line x1="12" y1="8.5" x2="18" y2="8.5"/><line x1="12" y1="12" x2="18" y2="12"/><line x1="12" y1="15.5" x2="17" y2="15.5"/><path d="M6 19.5h12"/>',
  PRINTER:
    '<path d="M6 9V3h12v6"/><rect x="6" y="13" width="12" height="8" rx="1"/><path d="M6 9H4a2 2 0 0 0-2 2v4h20v-4a2 2 0 0 0-2-2h-2"/><line x1="9" y1="16" x2="15" y2="16"/>',
  ROUTER:
    '<rect x="5" y="11" width="14" height="9" rx="1"/><path d="M9 11V7M12 11V5M15 11V7"/><circle cx="12" cy="15.5" r="1.5"/><path d="M10 18h4"/>',
  SWITCH:
    '<rect x="3" y="6" width="18" height="12" rx="1"/><line x1="7" y1="9" x2="7" y2="15"/><line x1="12" y1="9" x2="12" y2="15"/><line x1="17" y1="9" x2="17" y2="15"/><line x1="5" y1="11" x2="19" y2="11"/><line x1="5" y1="13" x2="19" y2="13"/>',
  PC: '<rect x="2" y="4" width="20" height="13" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>',
  NOTEBOOK:
    '<rect x="5" y="6" width="14" height="10" rx="1"/><path d="M3 18h18l-1.5 2h-15z"/><line x1="9" y1="11" x2="15" y2="11"/>',
  FIREWALL:
    '<path d="M12 3l8 4v6c0 4.5-2.5 8.5-8 10.5-5.5-2-8-6-8-10.5V7z"/><path d="M9 12l2 2 4-4"/>',
};

const TOPOLOGY_NODE_KIND_BG: Record<TopologyNodeKind, string> = Object.fromEntries(
  Object.entries(TOPOLOGY_NODE_KIND_INNER).map(([k, inner]) => [k, svgDataUrl(svgIcon(inner))]),
) as Record<TopologyNodeKind, string>;

export function topologyNodeKindInnerSvg(kind: unknown): string {
  if (typeof kind === 'string' && kind in TOPOLOGY_NODE_KIND_INNER) {
    return TOPOLOGY_NODE_KIND_INNER[kind as TopologyNodeKind];
  }
  return TOPOLOGY_NODE_KIND_INNER.RACK;
}

export function topologyNodeKindCanvasSvg(kind: unknown): string {
  return nodeCanvasSvgIcon(topologyNodeKindInnerSvg(kind));
}

export function topologyNodeKindBackgroundImage(kind: unknown): string {
  if (typeof kind === 'string' && kind in TOPOLOGY_NODE_KIND_BG) {
    return TOPOLOGY_NODE_KIND_BG[kind as TopologyNodeKind];
  }
  return TOPOLOGY_NODE_KIND_BG.RACK;
}
