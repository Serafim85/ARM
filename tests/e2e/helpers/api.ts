import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { agentIngestKey, backendURL, e2ePassword, e2eUser } from './stack';

export async function loginAccessToken(): Promise<string> {
  const response = await fetch(`${backendURL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: e2eUser, password: e2ePassword, authMode: 'LOCAL' }),
  });
  if (!response.ok) {
    throw new Error(`Login failed: HTTP ${response.status}`);
  }
  const body = (await response.json()) as { accessToken?: string };
  if (!body.accessToken) {
    throw new Error('Login response missing accessToken');
  }
  return body.accessToken;
}

export async function ingestLinuxWorkstation(hostname: string): Promise<void> {
  const fixturePath = path.resolve(__dirname, '../../fixtures/ingest-batch-linux.json');
  const raw = await readFile(fixturePath, 'utf8');
  const body = raw.replaceAll('pilot-linux-01', hostname);

  const response = await fetch(`${backendURL}/api/v1/agent/ingest`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Agent-Key': agentIngestKey,
    },
    body,
  });
  if (!response.ok) {
    throw new Error(`Ingest failed: HTTP ${response.status}`);
  }
}

export async function findWorkstationId(hostname: string, token: string): Promise<number> {
  const response = await fetch(
    `${backendURL}/api/v1/workstations?q=${encodeURIComponent(hostname)}`,
    {
      headers: { Authorization: `Bearer ${token}` },
    }
  );
  if (!response.ok) {
    throw new Error(`Workstations query failed: HTTP ${response.status}`);
  }
  const body = (await response.json()) as {
    content?: Array<{ id: number; hostname: string }>;
  };
  const match = body.content?.find((row) => row.hostname === hostname);
  if (!match?.id) {
    throw new Error(`Workstation not found: ${hostname}`);
  }
  return match.id;
}
