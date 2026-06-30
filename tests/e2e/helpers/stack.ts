export const backendURL = process.env.BACKEND_BASE_URL ?? 'http://localhost:8081';
export const agentIngestKey = process.env.AGENT_INGEST_API_KEY ?? 'dev-arm-ingest-key';
export const e2eUser = process.env.E2E_USER ?? 'admin@example.com';
export const e2ePassword = process.env.E2E_PASSWORD ?? 'password';

export async function isStackReachable(baseURL: string): Promise<boolean> {
  if (process.env.E2E_STACK_UP === '1') {
    return true;
  }
  try {
    const [frontend, backend] = await Promise.all([
      fetch(baseURL, { signal: AbortSignal.timeout(4000) }),
      fetch(`${backendURL}/api/public/app-config`, { signal: AbortSignal.timeout(4000) }),
    ]);
    return frontend.status < 500 && backend.ok;
  } catch {
    return false;
  }
}
