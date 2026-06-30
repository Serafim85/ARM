import { test, expect } from '@playwright/test';
import { findWorkstationId, ingestLinuxWorkstation, loginAccessToken } from '../../helpers/api';
import { loginViaUi } from '../../helpers/auth-ui';
import { isStackReachable } from '../../helpers/stack';

test.describe('Workstations list @mvp', () => {
  const hostname = `e2e-ws-${Date.now()}`;

  test.beforeAll(async ({ baseURL }) => {
    test.skip(!(await isStackReachable(baseURL!)), 'Start stack or set E2E_STACK_UP=1');
    await ingestLinuxWorkstation(hostname);
  });

  test('shows workstation from fixture ingest', async ({ page }) => {
    await loginViaUi(page);
    await page.goto('/workstations');
    await expect(page.getByRole('heading', { name: 'Рабочие станции' })).toBeVisible();
    await page.getByPlaceholder('Имя хоста, IP').fill(hostname);
    await page.getByRole('button', { name: 'Применить' }).click();
    await expect(page.getByRole('cell', { name: hostname })).toBeVisible({ timeout: 15_000 });
  });

  test('workstation is reachable via API after ingest', async () => {
    const token = await loginAccessToken();
    const id = await findWorkstationId(hostname, token);
    expect(id).toBeGreaterThan(0);
  });
});
